package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SubcategoryUpdateDTO {
    
    private String name;
    
    private String summary;
    
    private String image;
    
    @Pattern(regexp = "^\\d+$", message = "Display order must be a number")
    private String displayOrder;
}
