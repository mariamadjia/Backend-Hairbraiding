package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminCategoryDTO {
    private Long id;
    private String name;
    private String slug;
    private String summary;
    private String serviceTagline;
    private String serviceDescription;
    private String image;
    private Integer displayOrder;
    private List<String> flippingImages;
    private List<ImageResponse> galleryImages;
    private List<AdminSubcategoryDTO> subcategories;
    private List<AdminServiceItemDTO> items;
    private Long directServiceCount;
}
