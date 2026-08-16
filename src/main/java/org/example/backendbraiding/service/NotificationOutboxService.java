package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {
    private final NotificationOutboxRepository repository;
    private final AppointmentRepository appointmentRepository;
    private final AdminRepository adminRepository;

    public void enqueueEmail(Appointment appointment, String subject, String body) {
        String eventKey = UUID.randomUUID().toString();
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), subject, body,
                eventKey, eventKey + ":CUSTOMER_EMAIL");
    }

    public void enqueueSms(Appointment appointment, String body) {
        String eventKey = UUID.randomUUID().toString();
        enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null, body,
                eventKey, eventKey + ":CUSTOMER_SMS");
    }

    public void enqueueBoth(Appointment appointment, String subject, String emailBody, String smsBody) {
        String eventKey = UUID.randomUUID().toString();
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), subject, emailBody,
                eventKey, eventKey + ":CUSTOMER_EMAIL");
        if (smsBody != null && !smsBody.isBlank()) {
            enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null, smsBody,
                    eventKey, eventKey + ":CUSTOMER_SMS");
        }
    }

    public void enqueueCustomerAndAdmins(Appointment appointment, String customerSubject, String customerEmailBody,
                                         String customerSmsBody, String adminSubject, String adminBody) {
        String eventKey = UUID.randomUUID().toString();
        enqueue(appointment, NotificationOutbox.Channel.EMAIL, appointment.getCustomer().getEmail(), customerSubject,
                customerEmailBody, eventKey, eventKey + ":CUSTOMER_EMAIL");
        if (customerSmsBody != null && !customerSmsBody.isBlank()) {
            enqueue(appointment, NotificationOutbox.Channel.SMS, appointment.getCustomer().getPhoneNumber(), null,
                    customerSmsBody, eventKey, eventKey + ":CUSTOMER_SMS");
        }
        adminRepository.findAll().stream()
                .filter(admin -> "ACTIVE".equalsIgnoreCase(admin.getStatus()))
                .filter(admin -> admin.getEmail() != null && !admin.getEmail().isBlank())
                .filter(admin -> !admin.getEmail().equalsIgnoreCase(appointment.getCustomer().getEmail()))
                .forEach(admin -> enqueue(appointment, NotificationOutbox.Channel.EMAIL, admin.getEmail(), adminSubject,
                        adminBody, eventKey, eventKey + ":ADMIN_EMAIL:" + admin.getId()));
    }

    private void enqueue(Appointment appointment, NotificationOutbox.Channel channel, String recipient, String subject,
                         String body, String eventKey, String deliveryKey) {
        NotificationOutbox item = new NotificationOutbox();
        item.setAppointment(appointment);
        item.setChannel(channel);
        item.setRecipient(recipient);
        item.setSubject(subject);
        item.setBody(body);
        item.setEventKey(eventKey);
        item.setDeliveryKey(deliveryKey);
        repository.save(item);
        appointment.setNotificationStatus("PENDING");
        appointmentRepository.save(appointment);
    }
}
