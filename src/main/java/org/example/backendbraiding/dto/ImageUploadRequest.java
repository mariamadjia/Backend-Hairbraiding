package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class ImageUploadRequest {
    private String title;
    private String description;
    private String altText;
    private List<String> tags;
    private Long categoryId;
    private Long subcategoryId;
    private Long serviceItemId;
    private Boolean isFeatured = false;
    private Boolean isHero = false;
}
