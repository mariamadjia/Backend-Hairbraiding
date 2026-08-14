package org.example.backendbraiding.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "guide_settings")
@Data
public class GuideSettings {
    @Id private Long id = 1L;
    @Version private Long version = 0L;
    @Column(name = "length_guide_enabled", nullable = false) private Boolean lengthGuideEnabled = false;
    @Column(name = "size_guide_enabled", nullable = false) private Boolean sizeGuideEnabled = false;
    @Column(name = "length_guide_image_url", length = 2000) private String lengthGuideImageUrl;
}
