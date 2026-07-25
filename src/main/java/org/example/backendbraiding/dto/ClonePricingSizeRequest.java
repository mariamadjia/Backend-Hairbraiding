package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private Map<@NotBlank String, @NotNull Long> prices = new LinkedHashMap<>();
}
