package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryGalleryDTO {
    private Long id;
    private String name;
    private String slug;
    private String image;
    private Integer displayOrder;

    private List<String> flippingImages = new ArrayList<>();

    // New: photos taken from subcategory.image when no manual
    // flipping images have been selected yet.
    private List<String> fallbackImages = new ArrayList<>();

    private List<SubcategoryGalleryDTO> subcategories;
}
