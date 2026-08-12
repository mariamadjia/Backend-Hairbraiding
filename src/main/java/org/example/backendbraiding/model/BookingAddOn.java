package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_add_ons")
@Data
public class BookingAddOn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version = 0L;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "pricing_mode", nullable = false, length = 30)
    private String pricingMode = "FIXED";

    @Column(name = "price_cents", nullable = false)
    private Long priceCents = 0L;

    @Column(name = "deposit_behavior", nullable = false, length = 30)
    private String depositBehavior = "NO_CHANGE";

    @Column(name = "deposit_adjustment_cents", nullable = false)
    private Long depositAdjustmentCents = 0L;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
