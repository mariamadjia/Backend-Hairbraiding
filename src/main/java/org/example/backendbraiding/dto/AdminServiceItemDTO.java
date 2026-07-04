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
    private String link;
    private String objectPosition;
    private List<String> availableSizes;
    private List<String> hairTextures;
    private List<LengthOptionDTO> lengthOptions;
}
