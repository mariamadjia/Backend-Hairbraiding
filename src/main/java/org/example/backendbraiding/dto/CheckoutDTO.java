package org.example.backendbraiding.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CheckoutDTO(
        Long appointmentId,
        Long customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String serviceName,
        LocalDateTime appointmentDateTime,
        LocalDateTime appointmentEndDateTime,
        String appointmentStatus,
        String depositPaymentStatus,
        long serviceTotalCents,
        long depositCreditCents,
        long tipCents,
        long amountPaidCents,
        long amountRefundedCents,
        long balanceDueCents,
        String checkoutStatus,
        LocalDateTime completedAt,
        String notificationStatus,
        String bookingSource,
        String depositPolicyVersion,
        LocalDateTime depositPolicyAcceptedAt,
        String offSessionConsentPolicyVersion,
        LocalDateTime offSessionConsentAt,
        List<TransactionDTO> transactions
) {
    public record TransactionDTO(
            Long id,
            String type,
            String paymentMethod,
            String status,
            long amountCents,
            String externalReference,
            String reason,
            LocalDateTime createdAt
    ) {}
}
