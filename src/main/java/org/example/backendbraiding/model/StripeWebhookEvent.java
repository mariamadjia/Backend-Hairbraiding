package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stripe_webhook_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_stripe_webhook_event_id", columnNames = "stripe_event_id"))
@Data
@NoArgsConstructor
public class StripeWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Integer attempts = 1;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status { PROCESSING, PROCESSED, FAILED }
}
