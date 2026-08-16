package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {
    private final NotificationOutboxRepository repository;
    private final AppointmentRepository appointmentRepository;

    public void enqueueEmail(Appointment appointment, String subject, String body) {
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), subject, body,
                UUID.randomUUID().toString());
    }

    public void enqueueSms(Appointment appointment, String body) {
        enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null, body,
                UUID.randomUUID().toString());
    }

    public void enqueueBoth(Appointment appointment, String subject, String emailBody, String smsBody) {
        String eventKey = UUID.randomUUID().toString();
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), subject, emailBody, eventKey);
        if (smsBody != null && !smsBody.isBlank()) {
            enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null, smsBody, eventKey);
        }
    }

    private void enqueue(Appointment appointment, NotificationOutbox.Channel channel, String recipient, String subject,
                         String body, String eventKey) {
        NotificationOutbox item = new NotificationOutbox();
        item.setAppointment(appointment);
        item.setChannel(channel);
        item.setRecipient(recipient);
        item.setSubject(subject);
        item.setBody(body);
        item.setEventKey(eventKey);
        item.setDeliveryKey(eventKey + ":" + channel.name());
        repository.save(item);
        appointment.setNotificationStatus("PENDING");
        appointmentRepository.save(appointment);
    }
}
