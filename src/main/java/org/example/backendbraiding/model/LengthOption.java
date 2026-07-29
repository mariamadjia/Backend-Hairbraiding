package org.example.backendbraiding.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "length_options")
@Data
public class LengthOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String price;

    @Column(name = "knotless_price")
    private String knotlessPrice;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_item_id", nullable = false)
    @JsonIgnore
    private ServiceItem serviceItem;
}
