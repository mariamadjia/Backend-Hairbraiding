package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {
    private final NotificationOutboxRepository repository;
    private final AppointmentRepository appointmentRepository;

    public void enqueueEmail(Appointment appointment, String subject, String body) {
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), subject, body);
    }

    public void enqueueSms(Appointment appointment, String body) {
        enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null, body);
    }

    private void enqueue(Appointment appointment, NotificationOutbox.Channel channel, String recipient, String subject, String body) {
        NotificationOutbox item = new NotificationOutbox();
        item.setAppointment(appointment);
        item.setChannel(channel);
        item.setRecipient(recipient);
        item.setSubject(subject);
        item.setBody(body);
        repository.save(item);
        appointment.setNotificationStatus("PENDING");
        appointmentRepository.save(appointment);
    }
}
