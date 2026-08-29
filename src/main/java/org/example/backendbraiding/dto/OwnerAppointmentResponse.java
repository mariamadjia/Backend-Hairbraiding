package org.example.backendbraiding.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class OwnerAppointmentResponse {
    Long appointmentId;
    String appointmentStatus;
    String paymentStatus;
    Boolean depositRequired;
    Long depositAmount;
    Long remainingBalanceCents;
    LocalDateTime depositLinkExpiresAt;
    String depositPaymentUrl;
}
