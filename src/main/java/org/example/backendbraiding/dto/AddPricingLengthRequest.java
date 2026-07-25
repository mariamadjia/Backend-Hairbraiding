package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddPricingLengthRequest {
    @NotBlank @Size(max = 100)
    private String name;

    @NotEmpty @Size(max = 100)
    private List<Long> serviceIds = new ArrayList<>();

    @Min(1) @Max(1000000)
    private Long initialPriceCents;

    private String copyFromLengthName;

    @Min(-1000000) @Max(1000000)
    private Long adjustmentCents = 0L;
}
