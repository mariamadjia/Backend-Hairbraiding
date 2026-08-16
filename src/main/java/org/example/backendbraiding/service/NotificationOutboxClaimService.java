package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationOutboxClaimService {
    private final NotificationOutboxRepository repository;
    private final AppointmentRepository appointmentRepository;

    @Transactional
    public List<Long> claimPending() {
        LocalDateTime now = LocalDateTime.now();
        repository.findTop100ByStatusAndClaimedAtLessThanEqualOrderByCreatedAtAsc(
                NotificationOutbox.Status.PROCESSING, now.minusMinutes(10)).forEach(item -> {
            item.setStatus(NotificationOutbox.Status.PENDING);
            item.setClaimedAt(null);
            item.setLastError("Recovered after an interrupted delivery attempt");
        });
        List<NotificationOutbox> items = repository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(NotificationOutbox.Status.PENDING, now);
        items.forEach(item -> {
            item.setStatus(NotificationOutbox.Status.PROCESSING);
            item.setClaimedAt(now);
        });
        repository.saveAll(items);
        return items.stream().map(NotificationOutbox::getId).toList();
    }

    @Transactional(readOnly = true)
    public NotificationOutbox get(Long id) { return repository.findById(id).orElse(null); }

    @Transactional
    public void finish(Long id, boolean sent, String providerError) {
        NotificationOutbox item = repository.findById(id).orElse(null);
        if (item == null || item.getStatus() != NotificationOutbox.Status.PROCESSING) return;
        item.setAttempts(item.getAttempts() + 1);
        item.setClaimedAt(null);
        if (sent) {
            item.setStatus(NotificationOutbox.Status.SENT);
            item.setLastError(null);
        } else if (item.getAttempts() >= 5) {
            item.setStatus(NotificationOutbox.Status.FAILED);
            item.setLastError(providerError == null ? "Delivery failed after 5 attempts" : providerError);
        } else {
            item.setStatus(NotificationOutbox.Status.PENDING);
            item.setNextAttemptAt(LocalDateTime.now().plusMinutes(item.getAttempts()));
            item.setLastError(providerError == null ? "Delivery attempt failed" : providerError);
        }
        repository.save(item);
        updateAppointmentStatus(item.getAppointment());
    }

    @Transactional
    public boolean retryLatestFailed(Long appointmentId) {
        List<NotificationOutbox> all = repository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId);
        boolean reset = false;
        for (NotificationOutbox.Channel channel : NotificationOutbox.Channel.values()) {
            NotificationOutbox latest = all.stream().filter(item -> item.getChannel() == channel)
                    .max(Comparator.comparing(NotificationOutbox::getCreatedAt)).orElse(null);
            if (latest != null && latest.getStatus() == NotificationOutbox.Status.FAILED) {
                latest.setStatus(NotificationOutbox.Status.PENDING);
                latest.setAttempts(0);
                latest.setNextAttemptAt(LocalDateTime.now());
                latest.setLastError(null);
                repository.save(latest);
                reset = true;
            }
        }
        if (reset) appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            appointment.setNotificationStatus("PENDING");
            appointmentRepository.save(appointment);
        });
        return reset;
    }

    private void updateAppointmentStatus(Appointment appointment) {
        List<NotificationOutbox> all = repository.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId());
        String latestEvent = all.stream().map(NotificationOutbox::getEventKey).filter(Objects::nonNull).findFirst().orElse(null);
        List<NotificationOutbox> current = latestEvent == null ? List.of()
                : all.stream().filter(item -> latestEvent.equals(item.getEventKey())).toList();
        boolean pending = current.stream().anyMatch(item -> item.getStatus() == NotificationOutbox.Status.PENDING
                || item.getStatus() == NotificationOutbox.Status.PROCESSING);
        boolean failed = current.stream().anyMatch(item -> item.getStatus() == NotificationOutbox.Status.FAILED);
        appointment.setNotificationStatus(pending ? "PENDING" : failed ? "FAILED" : "SENT");
        appointment.setNotificationLastAttemptAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
    }
}
