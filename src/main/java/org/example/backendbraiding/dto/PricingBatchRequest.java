package org.example.backendbraiding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PricingBatchRequest {
    @NotEmpty
    @Size(max = 250)
    @Valid
    private List<ServicePriceChange> changes = new ArrayList<>();

    @Data
    public static class ServicePriceChange {
        @NotNull private Long serviceId;
        @NotNull @Min(0) private Long version;
        @Min(1) @Max(1000000) private Long basePriceCents;
        @Min(0) @Max(1000000) private Long knotlessAdjustmentCents;
        @Valid @Size(max = 50) private List<LengthPriceChange> lengths = new ArrayList<>();
    }

    @Data
    public static class LengthPriceChange {
        @NotNull private Long lengthOptionId;
        @NotNull @Min(1) @Max(1000000) private Long priceCents;
        @Min(1) @Max(1000000) private Long knotlessPriceCents;
        @NotNull @Min(0) private Integer displayOrder;
    }
}
