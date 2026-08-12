package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BookingQuoteRequest {
    @NotNull
    private Long serviceId;
    private Long lengthOptionId;
    @Size(max = 20)
    private String foundation;
    @Size(max = 20)
    private List<Long> addOnIds = new ArrayList<>();
}
