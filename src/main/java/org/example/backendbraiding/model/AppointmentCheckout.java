package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_checkouts")
@Data
@NoArgsConstructor
public class AppointmentCheckout {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version = 0L;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(name = "service_total_cents", nullable = false)
    private Long serviceTotalCents;

    @Column(name = "deposit_credit_cents", nullable = false)
    private Long depositCreditCents = 0L;

    @Column(name = "tip_cents", nullable = false)
    private Long tipCents = 0L;

    @Column(name = "amount_paid_cents", nullable = false)
    private Long amountPaidCents = 0L;

    @Column(name = "amount_refunded_cents", nullable = false)
    private Long amountRefundedCents = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.OPEN;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_admin_id")
    private Admin completedBy;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status { OPEN, PAID, PARTIALLY_REFUNDED, REFUNDED }
}
