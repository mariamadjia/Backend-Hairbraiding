package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxProcessor {
    private final NotificationOutboxClaimService claimService;
    private final EmailService emailService;
    private final SmsService smsService;

    @Scheduled(fixedDelayString = "${notifications.outbox.interval-ms:10000}")
    public void deliverPending() {
        for (Long id : claimService.claimPending()) {
            NotificationOutbox item = claimService.get(id);
            if (item == null) continue;
            boolean sent = item.getChannel() == NotificationOutbox.Channel.EMAIL
                    ? emailService.sendAppointmentUpdate(item.getRecipient(), item.getSubject(), item.getBody())
                    : smsService.sendSms(item.getRecipient(), item.getBody());
            claimService.finish(id, sent, sent ? null : "Notification provider rejected the delivery");
        }
    }
}
