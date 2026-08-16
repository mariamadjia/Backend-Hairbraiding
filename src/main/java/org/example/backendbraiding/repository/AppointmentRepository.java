package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {
    
    List<Appointment> findByStatus(Appointment.AppointmentStatus status);
    
    @EntityGraph(attributePaths = {"customer", "service", "service.subcategory", "approvedBy"})
    Page<Appointment> findByStatus(Appointment.AppointmentStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer", "service", "service.subcategory", "approvedBy"})
    Page<Appointment> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer", "service", "service.subcategory", "approvedBy"})
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);
    
    List<Appointment> findByCustomerId(Long customerId);

    Optional<Appointment> findFirstByCustomerIdAndAppointmentDateTimeOrderByIdDesc(
            Long customerId, LocalDateTime appointmentDateTime);
    
    @Query("SELECT a FROM Appointment a WHERE a.customer.id IN :customerIds")
    List<Appointment> findByCustomerIdIn(@Param("customerIds") List<Long> customerIds);
    
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :startDate " +
           "AND a.appointmentDateTime < :endDate ORDER BY a.appointmentDateTime ASC")
    @EntityGraph(attributePaths = {"customer", "service", "service.subcategory", "approvedBy"})
    Page<Appointment> findAppointmentsBetweenDates(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :fromDate " +
           "AND (a.status = 'APPROVED' OR (a.status = 'PENDING' AND a.approvedAt IS NOT NULL)) " +
           "ORDER BY a.appointmentDateTime ASC")
    @EntityGraph(attributePaths = {"customer", "service", "service.subcategory", "approvedBy"})
    Page<Appointment> findActiveUpcomingAppointments(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);
    
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.appointmentDateTime >= :fromDate ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findUpcomingAppointmentsByStatus(
        @Param("status") Appointment.AppointmentStatus status,
        @Param("fromDate") LocalDateTime fromDate
    );
    
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :fromDate ORDER BY a.appointmentDateTime ASC")
    List<Appointment> findUpcomingAppointments(@Param("fromDate") LocalDateTime fromDate);
    
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDateTime < :end " +
           "AND ((a.appointmentEndDateTime IS NOT NULL AND a.appointmentEndDateTime > :start) " +
           "OR (a.appointmentEndDateTime IS NULL AND a.appointmentDateTime >= :start)) " +
           "AND a.status != 'DENIED' AND a.status != 'CANCELLED' " +
           "AND (a.status != 'PENDING' OR a.paymentStatus = 'AUTHORIZED' OR a.paymentStatus = 'CAPTURED' " +
           "OR a.paymentPendingExpiresAt IS NULL OR a.paymentPendingExpiresAt > :now)")
    long countOverlapping(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDateTime = :start " +
           "AND a.status != 'DENIED' AND a.status != 'CANCELLED' " +
           "AND (a.status != 'PENDING' OR a.paymentStatus = 'AUTHORIZED' OR a.paymentStatus = 'CAPTURED' " +
           "OR a.paymentPendingExpiresAt IS NULL OR a.paymentPendingExpiresAt > :now)")
    long countActiveAtStart(@Param("start") LocalDateTime start,
                            @Param("now") LocalDateTime now);

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :start " +
           "AND a.appointmentDateTime < :end " +
           "AND a.status != 'DENIED' AND a.status != 'CANCELLED' " +
           "AND (a.status != 'PENDING' OR a.paymentStatus = 'AUTHORIZED' OR a.paymentStatus = 'CAPTURED' " +
           "OR a.paymentPendingExpiresAt IS NULL OR a.paymentPendingExpiresAt > :now)")
    List<Appointment> findActiveStartsBetween(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("now") LocalDateTime now);

    @Query("SELECT a FROM Appointment a WHERE a.status = 'PENDING' AND a.paymentStatus = 'PENDING' " +
           "AND a.paymentPendingExpiresAt IS NOT NULL AND a.paymentPendingExpiresAt < :now")
    List<Appointment> findExpiredPendingReservations(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM Appointment a WHERE a.status = 'PENDING' AND a.paymentStatus = 'FAILED'")
    List<Appointment> findFailedPendingReservations();

    @Query("SELECT a FROM Appointment a WHERE a.paymentStatus = 'AUTHORIZED' " +
           "AND a.paymentAuthorizationExpiresAt IS NOT NULL AND a.paymentAuthorizationExpiresAt <= :now")
    List<Appointment> findExpiredAuthorizations(@Param("now") LocalDateTime now);
    
    Optional<Appointment> findByPaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a JOIN FETCH a.customer WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT a FROM Appointment a WHERE a.paymentIntentId IS NOT NULL AND a.paymentStatus IN " +
           "('PENDING', 'AUTHORIZED', 'CAPTURE_FAILED', 'CANCELLATION_FAILED')")
    List<Appointment> findAppointmentsNeedingPaymentReconciliation();
}
