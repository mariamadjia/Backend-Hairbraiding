package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NoShowFeeDTO {
    private Long appointmentId;
    private Long scheduledServicePriceCents;
    private Integer feeRatePercent;
    private Long totalFeeCents;
    private Long depositCreditCents;
    private Long amountToChargeCents;
    private String feeDecision;
    private String paymentStatus;
    private String paymentMethodBrand;
    private String paymentMethodLast4;
    private String failureMessage;
    private LocalDateTime eligibleAt;
    private LocalDateTime normalDeadlineAt;
    private LocalDateTime automaticChargeDeadlineAt;
    private boolean overdueConfirmationRequired;
    private boolean automaticChargeAllowed;
    private boolean bookingRestricted;
    private LocalDateTime consentRecordedAt;
    private Integer chargeAttemptCount;
    private LocalDateTime chargeAttemptedAt;
    private LocalDateTime paidAt;
    private String adminNote;
}
