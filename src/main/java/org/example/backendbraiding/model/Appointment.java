package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appointment_status_datetime", columnList = "status, appointment_date_time"),
    @Index(name = "idx_appointment_customer", columnList = "customer_id"),
    @Index(name = "idx_appointment_datetime", columnList = "appointment_date_time"),
    @Index(name = "idx_appointment_customer_datetime", columnList = "customer_id, appointment_date_time", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    @Column(name = "selected_size")
    private String selectedSize;

    @Column(name = "selected_length")
    private String selectedLength;

    @Column(name = "selected_service", length = 100)
    private String selectedService;

    @Column(name = "price")
    private String price;

    @Column(nullable = false)
    private LocalDateTime appointmentDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 1000)
    private String notes;

    @Column(length = 500)
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Admin approvedBy;

    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "deposit_amount")
    private Long depositAmount;

    @Column(name = "payment_captured_at")
    private LocalDateTime paymentCapturedAt;

    @Column(name = "payment_method_last4")
    private String paymentMethodLast4;

    @Column(name = "payment_method_brand")
    private String paymentMethodBrand;

    public enum AppointmentStatus {
        PENDING,
        APPROVED,
        DENIED,
        CANCELLED,
        COMPLETED
    }

    public enum PaymentStatus {
        PENDING,
        AUTHORIZED,
        CAPTURED,
        CANCELLED,
        FAILED
    }
}
