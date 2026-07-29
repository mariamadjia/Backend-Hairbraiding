package org.example.backendbraiding.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategorySummaryDTO {
    private Long id;
    private String name;
    private String slug;
    private Integer displayOrder;
    private Long styleCount;
    private LocalDateTime updatedAt;

    public CategorySummaryDTO() {}

    public CategorySummaryDTO(
            Long id,
            String name,
            String slug,
            Integer displayOrder,
            Long styleCount,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
        this.styleCount = styleCount;
        this.updatedAt = updatedAt;
    }
}
