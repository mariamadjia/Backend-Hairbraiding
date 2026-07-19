package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SizePricingDTO {
    private String name;
    private List<LengthPricingDTO> lengths;
}
