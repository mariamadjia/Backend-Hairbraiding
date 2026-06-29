package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImageUpdateRequest {
    private String title;
    private String description;
    private String altText;
    private List<String> tags;
    private Long categoryId;
    private Long subcategoryId;
    private Long serviceItemId;
    private Boolean isFeatured;
    private Boolean isHero;
    private Integer displayOrder;
}
