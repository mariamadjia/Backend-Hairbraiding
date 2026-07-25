package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingQuoteRequest {
    @NotNull
    private Long serviceId;
    private Long lengthOptionId;
    @Size(max = 20)
    private String foundation;
}
