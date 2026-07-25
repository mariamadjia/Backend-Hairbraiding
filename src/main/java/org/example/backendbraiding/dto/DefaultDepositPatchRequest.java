package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DefaultDepositPatchRequest {
    @NotNull @Min(0)
    private Long version;

    @NotNull @Min(1) @Max(100000)
    private Long depositCents;
}
