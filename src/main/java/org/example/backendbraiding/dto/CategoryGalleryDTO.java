package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class CategoryGalleryDTO {
    private Long id;
    private String name;
    private String slug;
    private String image;
    private List<String> flippingImages;
    private List<SubcategoryGalleryDTO> subcategories;
}
