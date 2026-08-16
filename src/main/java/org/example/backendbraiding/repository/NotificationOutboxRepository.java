package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NotificationOutbox> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            NotificationOutbox.Status status, LocalDateTime now);
    List<NotificationOutbox> findByAppointmentId(Long appointmentId);
    List<NotificationOutbox> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
    List<NotificationOutbox> findTop100ByStatusAndClaimedAtLessThanEqualOrderByCreatedAtAsc(
            NotificationOutbox.Status status, LocalDateTime claimedBefore);
}
