package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ClonePricingSizeRequest {
    @NotNull
    private Long cloneFromServiceId;

    @NotBlank @Size(max = 120)
    private String name;

    /** Prices keyed by the existing length name, or "Base price" for services without lengths. */
    @NotNull @Size(max = 50)
    private Map<@NotBlank String, @NotNull @Min(1) @Max(1000000) Long> prices = new LinkedHashMap<>();

    /** Required per-length prices when the source uses separate Knotless pricing. */
    @Size(max = 50)
    private Map<@NotBlank String, @NotNull @Min(1) @Max(1000000) Long> knotlessPrices = new LinkedHashMap<>();
}
