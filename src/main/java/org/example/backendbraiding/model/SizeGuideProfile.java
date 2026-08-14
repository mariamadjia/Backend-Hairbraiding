package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "size_guide_profiles", uniqueConstraints = @UniqueConstraint(name = "uq_size_guide_key", columnNames = "guide_key"))
@Data
public class SizeGuideProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version = 0L;
    @Column(name = "guide_key", nullable = false, length = 40) private String guideKey;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "image_url", length = 2000) private String imageUrl;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
}
