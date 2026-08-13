package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxProcessor {
    private final NotificationOutboxRepository repository;
    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    @Scheduled(fixedDelayString = "${notifications.outbox.interval-ms:10000}")
    @Transactional
    public void deliverPending() {
        for (NotificationOutbox item : repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                NotificationOutbox.Status.PENDING, LocalDateTime.now())) {
            boolean sent = item.getChannel() == NotificationOutbox.Channel.EMAIL
                    ? emailService.sendAppointmentUpdate(item.getRecipient(), item.getSubject(), item.getBody())
                    : smsService.sendSms(item.getRecipient(), item.getBody());
            item.setAttempts(item.getAttempts() + 1);
            if (sent) {
                item.setStatus(NotificationOutbox.Status.SENT);
                item.setLastError(null);
            } else if (item.getAttempts() >= 5) {
                item.setStatus(NotificationOutbox.Status.FAILED);
                item.setLastError("Delivery failed after 5 attempts");
            } else {
                item.setNextAttemptAt(LocalDateTime.now().plusMinutes(item.getAttempts()));
                item.setLastError("Delivery attempt failed");
            }
            repository.save(item);
            updateAppointmentStatus(item.getAppointment());
        }
    }

    private void updateAppointmentStatus(Appointment appointment) {
        List<NotificationOutbox> all = repository.findByAppointmentId(appointment.getId());
        boolean pending = all.stream().anyMatch(item -> item.getStatus() == NotificationOutbox.Status.PENDING);
        boolean failed = List.of(NotificationOutbox.Channel.EMAIL, NotificationOutbox.Channel.SMS).stream()
                .map(channel -> all.stream().filter(item -> item.getChannel() == channel)
                        .max(java.util.Comparator.comparing(NotificationOutbox::getCreatedAt)).orElse(null))
                .filter(java.util.Objects::nonNull)
                .anyMatch(item -> item.getStatus() == NotificationOutbox.Status.FAILED);
        appointment.setNotificationStatus(pending ? "PENDING" : failed ? "FAILED" : "SENT");
        appointment.setNotificationLastAttemptAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
    }
}
