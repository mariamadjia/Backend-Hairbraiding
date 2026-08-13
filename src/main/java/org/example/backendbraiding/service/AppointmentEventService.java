package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.AppointmentEvent;
import org.example.backendbraiding.repository.AppointmentEventRepository;
import org.springframework.stereotype.Service;
import org.example.backendbraiding.dto.AppointmentEventDTO;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentEventService {
    private final AppointmentEventRepository repository;

    public void record(Appointment appointment, String eventType, Admin actor, String reason) {
        AppointmentEvent event = new AppointmentEvent();
        event.setAppointment(appointment);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setAppointmentStatus(appointment.getStatus().name());
        event.setPaymentStatus(appointment.getPaymentStatus() == null ? null : appointment.getPaymentStatus().name());
        event.setReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000)));
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AppointmentEventDTO> history(Long appointmentId) {
        return repository.findByAppointmentIdOrderByCreatedAtAsc(appointmentId).stream().map(event ->
                new AppointmentEventDTO(event.getId(), event.getEventType(), event.getAppointmentStatus(),
                        event.getPaymentStatus(), event.getActor() == null ? null
                        : event.getActor().getFirstName() + " " + event.getActor().getLastName(),
                        event.getReason(), event.getCreatedAt())).toList();
    }
}
