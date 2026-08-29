package org.example.backendbraiding.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class OwnerDepositPageDTO {
    Long appointmentId;
    String customerName;
    String serviceName;
    String selectedSize;
    String selectedLength;
    String selectedFoundation;
    String selectedTexture;
    List<QuotedAddOnDTO> addOns;
    LocalDateTime appointmentDateTime;
    Long serviceTotalCents;
    Long depositDueCents;
    Long remainingBalanceCents;
    LocalDateTime expiresAt;
    String paymentStatus;
    String appointmentStatus;
    String clientSecret;
}
