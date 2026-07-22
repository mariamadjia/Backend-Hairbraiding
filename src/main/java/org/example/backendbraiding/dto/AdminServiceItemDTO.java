package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminServiceItemDTO {
    private Long id;
    private String name;
    private String price;
    private String description;
    private String notes;
    private String image;
    private List<String> images;
    private List<String> sizePhotos;
    private String link;
    private String objectPosition;
    private Boolean foundationChoicesEnabled;
    private String knotlessPriceAdjustment;
    private List<String> availableSizes;
    private List<String> hairTextures;
    private List<LengthOptionDTO> lengthOptions;
    private Integer displayOrder;
}
