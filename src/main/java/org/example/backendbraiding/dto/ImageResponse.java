package org.example.backendbraiding.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ImageResponse {
    private Long id;
    private String title;
    private String description;
    private String imageUrl;
    private String thumbnailUrl;
    private String altText;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String mimeType;
    private Integer displayOrder;
    private Boolean isFeatured;
    private Boolean isHero;
    private List<String> tags;
    private Long categoryId;
    private String categoryName;
    private Long subcategoryId;
    private String subcategoryName;
    private Integer subcategoryDisplayOrder;
    private Long serviceItemId;
    private String serviceItemName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String uploadedBy;
}
