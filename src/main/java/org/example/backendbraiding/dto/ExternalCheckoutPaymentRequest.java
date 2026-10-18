package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExternalCheckoutPaymentRequest(
        @NotBlank String paymentMethod,
        @Min(0) @Max(1_000_000) Long tipCents,
        @Size(max = 255) String externalReference,
        @Size(max = 500) String adminNotes
) {}
