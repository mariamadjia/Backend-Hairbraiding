package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubcategoryGalleryDTO {
    private Long id;
    private String name;
    private String slug;
    private String image;
    private List<String> images;
    private List<String> imageAltTexts;
}
