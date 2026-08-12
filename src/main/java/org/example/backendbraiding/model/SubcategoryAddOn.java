package org.example.backendbraiding.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "subcategory_add_ons", uniqueConstraints =
        @UniqueConstraint(name = "uq_subcategory_add_on", columnNames = {"subcategory_id", "add_on_id"}))
@Data
public class SubcategoryAddOn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcategory_id", nullable = false)
    @JsonIgnore
    private Subcategory subcategory;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "add_on_id", nullable = false)
    private BookingAddOn addOn;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "price_override_cents")
    private Long priceOverrideCents;

    @Column(name = "all_sizes", nullable = false)
    private Boolean allSizes = true;

    @Column(name = "all_lengths", nullable = false)
    private Boolean allLengths = true;

    @ElementCollection
    @CollectionTable(name = "subcategory_add_on_service_items", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "service_item_id")
    private Set<Long> serviceItemIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "subcategory_add_on_length_options", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "length_option_id")
    private Set<Long> lengthOptionIds = new LinkedHashSet<>();
}
