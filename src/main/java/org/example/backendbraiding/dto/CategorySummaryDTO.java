package org.example.backendbraiding.dto;

import lombok.Data;

@Data
public class CategorySummaryDTO {
    private Long id;
    private String name;
    private String slug;
    private Integer displayOrder;
}
