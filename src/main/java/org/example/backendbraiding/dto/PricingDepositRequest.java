package org.example.backendbraiding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PricingDepositRequest {
    @NotNull
    @Min(0)
    private Long version;

    @NotNull
    @Min(1) @Max(100000)
    private Long defaultDepositCents;

    @Valid
    private List<ServiceOverride> overrides = new ArrayList<>();

    @Data
    public static class ServiceOverride {
        @NotNull private Long serviceId;
        @NotNull @Min(0) private Long version;
        @Min(1) @Max(100000) private Long depositCents;
    }
}
