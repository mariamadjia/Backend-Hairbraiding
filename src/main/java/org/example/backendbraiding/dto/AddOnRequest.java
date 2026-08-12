package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddOnRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 1000)
    private String description;

    private String pricingMode = "FIXED";

    @Min(0) @Max(10000000)
    private Long priceCents = 0L;

    private String depositBehavior = "NO_CHANGE";

    @Min(0) @Max(1000000)
    private Long depositAdjustmentCents = 0L;

    private Boolean active = true;
    private List<Long> subcategoryIds = new ArrayList<>();
    private Boolean allSizes = true;
    private Boolean allLengths = true;
    private List<Long> serviceItemIds = new ArrayList<>();
    private List<Long> lengthOptionIds = new ArrayList<>();
}
