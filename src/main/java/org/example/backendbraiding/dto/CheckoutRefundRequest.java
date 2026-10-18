package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRefundRequest(
        @NotNull @Min(1) Long amountCents,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 255) String externalReference
) {}
