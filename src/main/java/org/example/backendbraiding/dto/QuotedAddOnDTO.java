package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotedAddOnDTO {
    private Long id;
    private String name;
    private String pricingMode;
    private Long advertisedPriceCents;
    private Long chargedPriceCents;
    private Boolean confirmationRequired;
}
