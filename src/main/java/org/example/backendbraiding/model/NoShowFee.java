package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_no_show_fees")
@Data
@NoArgsConstructor
public class NoShowFee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version = 0L;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;
    @Column(name = "scheduled_service_price_cents", nullable = false)
    private Long scheduledServicePriceCents;
    @Column(name = "fee_rate_percent", nullable = false)
    private Integer feeRatePercent;
    @Column(name = "total_fee_cents", nullable = false)
    private Long totalFeeCents;
    @Column(name = "deposit_credit_cents", nullable = false)
    private Long depositCreditCents;
    @Column(name = "amount_to_charge_cents", nullable = false)
    private Long amountToChargeCents;
    @Enumerated(EnumType.STRING) @Column(name = "fee_decision", nullable = false, length = 20)
    private FeeDecision feeDecision = FeeDecision.ACTIVE;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;
    @Column(name = "charge_attempt_count", nullable = false)
    private Integer chargeAttemptCount = 0;
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
    @Column(name = "admin_note", length = 500)
    private String adminNote;
    @Column(name = "marked_at", nullable = false)
    private LocalDateTime markedAt;
    @Column(name = "charge_attempted_at")
    private LocalDateTime chargeAttemptedAt;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum FeeDecision { ACTIVE, ADJUSTED, WAIVED }
    public enum PaymentStatus { UNPAID, PROCESSING, PAID, FAILED }
}
