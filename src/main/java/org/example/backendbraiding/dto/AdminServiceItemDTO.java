package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminServiceItemDTO {
    private Long id;
    private Long version;
    private String name;
    private String price;
    private String pricingMode;
    private String description;
    private String notes;
    private Integer durationMinutes;
    private String image;
    private List<String> images;
    private List<String> sizePhotos;
    private String link;
    private String objectPosition;
    private Boolean foundationChoicesEnabled;
    private String knotlessPriceAdjustment;
    private String knotlessPricingMode;
    private Long depositOverrideCents;
    private List<String> availableSizes;
    private List<String> hairTextures;
    private List<LengthOptionDTO> lengthOptions;
    private Integer displayOrder;
}
