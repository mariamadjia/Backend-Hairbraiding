package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryPricingDTO {
    private String name;
    private List<SizePricingDTO> sizes;
}
