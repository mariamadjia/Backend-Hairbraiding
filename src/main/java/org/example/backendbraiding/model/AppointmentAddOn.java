package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "appointment_add_ons")
@Data
public class AppointmentAddOn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "add_on_id")
    private BookingAddOn addOn;

    @Column(name = "add_on_name", nullable = false, length = 120)
    private String addOnName;

    @Column(name = "pricing_mode", nullable = false, length = 30)
    private String pricingMode;

    @Column(name = "advertised_price_cents", nullable = false)
    private Long advertisedPriceCents;

    @Column(name = "charged_price_cents", nullable = false)
    private Long chargedPriceCents;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
