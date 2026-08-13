package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.AppointmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentEventRepository extends JpaRepository<AppointmentEvent, Long> {
    List<AppointmentEvent> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);
}
