package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_transactions")
@Data
@NoArgsConstructor
public class CheckoutTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_id", nullable = false)
    private AppointmentCheckout checkout;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private Type transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUCCEEDED;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(length = 500)
    private String reason;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_admin_id")
    private Admin recordedBy;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Type { PAYMENT, REFUND }
    public enum PaymentMethod { CASH, ZELLE, CASH_APP, OTHER, STRIPE_TERMINAL }
    public enum Status { SUCCEEDED, FAILED }
}
