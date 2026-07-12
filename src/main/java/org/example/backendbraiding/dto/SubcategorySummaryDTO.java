package org.example.backendbraiding.dto;

import lombok.Data;

@Data
public class SubcategorySummaryDTO {
    private Long id;
    private String name;
    private String slug;
    private Integer displayOrder;

    public SubcategorySummaryDTO() {}

    public SubcategorySummaryDTO(Long id, String name, String slug, Integer displayOrder) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
    }
}
