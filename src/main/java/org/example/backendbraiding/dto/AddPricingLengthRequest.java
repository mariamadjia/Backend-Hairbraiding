package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddPricingLengthRequest {
    @NotBlank @Size(max = 100)
    private String name;

    @Size(max = 100)
    private List<Long> serviceIds = new ArrayList<>();

    @Valid
    @Size(max = 100)
    private List<ServicePrice> servicePrices = new ArrayList<>();

    @Min(1) @Max(1000000)
    private Long initialPriceCents;

    private String copyFromLengthName;

    @Min(-1000000) @Max(1000000)
    private Long adjustmentCents = 0L;

    @Data
    public static class ServicePrice {
        @NotNull
        private Long serviceId;

        @NotNull @Min(0)
        private Long version;

        @NotNull @Min(1) @Max(1000000)
        private Long priceCents;

        @Min(1) @Max(1000000)
        private Long knotlessPriceCents;
    }
}
