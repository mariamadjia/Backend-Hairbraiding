package org.example.backendbraiding.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BookingQuoteResponse {
    private Long serviceId;
    private Long lengthOptionId;
    private String servicePrice;
    private Long servicePriceCents;
    private Long depositCents;
    private Long remainingBalanceCents;
    private Long serviceVersion;
    private String quoteToken;
    private Instant expiresAt;
}
