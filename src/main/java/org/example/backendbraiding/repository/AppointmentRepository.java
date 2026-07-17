package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    List<Appointment> findByStatus(Appointment.AppointmentStatus status);
    
    Page<Appointment> findByStatus(Appointment.AppointmentStatus status, Pageable pageable);
    
    List<Appointment> findByCustomerId(Long customerId);
    
    @Query("SELECT a FROM Appointment a WHERE a.customer.id IN :customerIds")
    List<Appointment> findByCustomerIdIn(@Param("customerIds") List<Long> customerIds);
    
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime BETWEEN :startDate AND :endDate")
    Page<Appointment> findAppointmentsBetweenDates(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.appointmentDateTime >= :fromDate ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findUpcomingAppointmentsByStatus(
        @Param("status") Appointment.AppointmentStatus status,
        @Param("fromDate") LocalDateTime fromDate
    );
    
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :fromDate ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findUpcomingAppointments(@Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDateTime >= :start AND a.appointmentDateTime < :end AND a.status != 'DENIED' AND a.status != 'CANCELLED'")
    long countByAppointmentDateTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    Optional<Appointment> findByPaymentIntentId(String paymentIntentId);
}
