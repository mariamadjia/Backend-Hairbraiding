package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubcategoryRequestDTO {
    
    @NotBlank(message = "Subcategory name is required")
    private String name;
    
    @NotNull(message = "Category ID is required")
    private Long categoryId;
    
    private String summary;
    
    private String image;
    
    private Integer displayOrder;
}
