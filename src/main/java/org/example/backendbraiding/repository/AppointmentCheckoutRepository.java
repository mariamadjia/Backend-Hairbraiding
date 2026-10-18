package org.example.backendbraiding.repository;

import jakarta.persistence.LockModeType;
import org.example.backendbraiding.model.AppointmentCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppointmentCheckoutRepository extends JpaRepository<AppointmentCheckout, Long> {
    Optional<AppointmentCheckout> findByAppointmentId(Long appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AppointmentCheckout c WHERE c.appointment.id = :appointmentId")
    Optional<AppointmentCheckout> findByAppointmentIdForUpdate(@Param("appointmentId") Long appointmentId);
}
