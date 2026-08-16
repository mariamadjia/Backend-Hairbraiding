package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentNotificationDispatchService {
    private final AppointmentRepository appointmentRepository;
    private final NotificationOutboxService outboxService;
    private final AppointmentNotificationTemplates templates;

    @Transactional
    public void denied(Long appointmentId) { enqueue(appointmentId, Kind.DENIED); }

    @Transactional
    public void cancelled(Long appointmentId) { enqueue(appointmentId, Kind.CANCELLED); }

    @Transactional
    public void expired(Long appointmentId) { enqueue(appointmentId, Kind.EXPIRED); }

    private void enqueue(Long appointmentId, Kind kind) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        AppointmentNotificationTemplates.Notification notification = switch (kind) {
            case DENIED -> templates.denied(appointment);
            case CANCELLED -> templates.cancelled(appointment);
            case EXPIRED -> templates.expired(appointment);
        };
        outboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
    }

    private enum Kind { DENIED, CANCELLED, EXPIRED }
}
