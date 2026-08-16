package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.NoShowFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface NoShowFeeRepository extends JpaRepository<NoShowFee, Long> {
    Optional<NoShowFee> findByAppointmentId(Long appointmentId);
    Optional<NoShowFee> findByStripePaymentIntentId(String paymentIntentId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM NoShowFee f " +
            "WHERE f.appointment.customer.id = :customerId AND f.feeDecision != 'WAIVED' AND f.paymentStatus != 'PAID'")
    boolean hasUnresolvedBalance(@Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM NoShowFee f JOIN FETCH f.appointment a JOIN FETCH a.customer WHERE f.id = :id")
    Optional<NoShowFee> findByIdForUpdate(@Param("id") Long id);
}
