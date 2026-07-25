package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServiceDepositPatchRequest {
    @NotNull @Min(0)
    private Long version;

    @Min(1) @Max(100000)
    private Long depositCents;
}
