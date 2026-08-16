package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_outbox", indexes = @Index(name = "idx_notification_outbox_delivery", columnList = "status, next_attempt_at"))
@Data
@NoArgsConstructor
public class NotificationOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Channel channel;
    @Column(nullable = false)
    private String recipient;
    @Column(length = 255)
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;
    @Column(name = "delivery_key", nullable = false, length = 100, unique = true)
    private String deliveryKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    @Column(nullable = false)
    private Integer attempts = 0;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Channel { EMAIL, SMS }
    public enum Status { PENDING, PROCESSING, SENT, FAILED }
}
