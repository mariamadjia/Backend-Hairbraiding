package org.example.backendbraiding.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.PaymentCaptureRequest;
import org.example.backendbraiding.dto.PaymentIntentRequest;
import org.example.backendbraiding.dto.PaymentIntentResponse;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final long DEPOSIT_AMOUNT_CENTS = 5000L;

    private final AppointmentRepository appointmentRepository;
    private final BookingPaymentTokenService bookingPaymentTokenService;
    private final SmsService smsService;
    private final EmailService emailService;

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        if (!bookingPaymentTokenService.isValidForAppointment(request.getPaymentToken(), request.getAppointmentId())) {
            throw new IllegalArgumentException("Invalid or expired payment token");
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be authorized for a pending appointment");
        }
        if (appointment.getPaymentPendingExpiresAt() != null
                && !appointment.getPaymentPendingExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Booking reservation has expired");
        }

        try {
            String replacedIntentId = null;
            if (appointment.getPaymentIntentId() != null) {
                PaymentIntent existingIntent = PaymentIntent.retrieve(appointment.getPaymentIntentId());
                if (PaymentLifecycleRules.isReusableForConfirmation(existingIntent.getStatus())
                        && existingIntent.getAutomaticPaymentMethods() != null
                        && Boolean.TRUE.equals(existingIntent.getAutomaticPaymentMethods().getEnabled())) {
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment intent ready for authorization.");
                }
                if ("requires_capture".equals(existingIntent.getStatus())) {
                    recordAuthorization(appointment);
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment is already authorized.");
                }
                if ("succeeded".equals(existingIntent.getStatus())) {
                    recordCapture(appointment);
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment is already complete.");
                }
                replacedIntentId = existingIntent.getId();
                appointment.setPaymentIntentId(null);
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("appointmentId", appointment.getId().toString());
            metadata.put("customerEmail", appointment.getCustomer().getEmail());
            metadata.put("customerName", appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName());

            long depositAmountCents = calculateDepositAmountCents(appointment.getPrice());

            PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(depositAmountCents)
                    .setCurrency("usd")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .putAllMetadata(metadata)
                    .build(), RequestOptions.builder()
                    .setIdempotencyKey(replacedIntentId == null
                            ? "booking-payment-intent-dynamic-v2-" + appointment.getId()
                            : "booking-payment-intent-retry-" + appointment.getId() + "-" + replacedIntentId)
                    .build());

            appointment.setPaymentIntentId(paymentIntent.getId());
            appointment.setDepositAmount(depositAmountCents);
            appointment.setPaymentStatus(Appointment.PaymentStatus.PENDING);
            appointmentRepository.save(appointment);

            return paymentIntentResponse(paymentIntent, appointment.getId(), "Payment intent created successfully.");
        } catch (StripeException e) {
            log.error("Error creating payment intent: {}", e.getMessage(), e);
            throw new org.example.backendbraiding.exception.PaymentProcessingException("Payment provider could not create the authorization");
        }
    }

    private void recordAuthorization(Appointment appointment) {
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setPaymentPendingExpiresAt(null);
        // Stripe authorization windows vary by method. Six days is a conservative
        // operational deadline for the shortest commonly enabled methods.
        appointment.setPaymentAuthorizationExpiresAt(LocalDateTime.now().plusDays(6));
        appointmentRepository.save(appointment);
    }

    private void recordCapture(Appointment appointment) {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
        appointment.setPaymentCapturedAt(LocalDateTime.now());
        appointment.setPaymentAuthorizationExpiresAt(null);
        boolean notifyApproval = appointment.getStatus() == Appointment.AppointmentStatus.APPROVAL_PENDING_CAPTURE;
        if (notifyApproval) {
            appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
        }
        appointmentRepository.save(appointment);
        if (notifyApproval) {
            smsService.sendAppointmentApprovedSms(
                    appointment.getCustomer().getPhoneNumber(),
                    appointment.getCustomer().getFirstName(),
                    appointment.getAppointmentDateTime().toString());
            emailService.sendAppointmentUpdate(
                    appointment.getCustomer().getEmail(),
                    "Appointment approved",
                    "Your appointment for " + appointment.getAppointmentDateTime() + " Central Time has been approved.");
        }
    }

    private long calculateDepositAmountCents(String appointmentPrice) {
        if (appointmentPrice == null || appointmentPrice.isBlank()) {
            return DEPOSIT_AMOUNT_CENTS;
        }
        try {
            BigDecimal price = new BigDecimal(appointmentPrice.replaceAll("[^0-9.]", ""))
                    .setScale(2, RoundingMode.HALF_UP);
            long priceCents = price.movePointRight(2).longValueExact();
            if (priceCents <= 0) {
                throw new IllegalArgumentException("Appointment price must be greater than zero");
            }
            return Math.min(DEPOSIT_AMOUNT_CENTS, priceCents);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalStateException("Appointment price is invalid", exception);
        }
    }

    private PaymentIntentResponse paymentIntentResponse(PaymentIntent paymentIntent, Long appointmentId, String message) {
        return PaymentIntentResponse.builder()
                .paymentIntentId(paymentIntent.getId())
                .clientSecret(paymentIntent.getClientSecret())
                .status(paymentIntent.getStatus())
                .amount(paymentIntent.getAmount())
                .currency(paymentIntent.getCurrency())
                .message(message)
                .appointmentId(appointmentId)
                .build();
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse capturePayment(PaymentCaptureRequest request) {
        try {
            PaymentIntent paymentIntent;
            
            if (request.getAmountToCapture() != null) {
                PaymentIntentCaptureParams params = PaymentIntentCaptureParams.builder()
                        .setAmountToCapture(request.getAmountToCapture())
                        .build();
                paymentIntent = PaymentIntent.retrieve(request.getPaymentIntentId()).capture(params);
            } else {
                paymentIntent = PaymentIntent.retrieve(request.getPaymentIntentId()).capture();
            }

            Appointment appointment = appointmentRepository.findByPaymentIntentId(request.getPaymentIntentId())
                    .orElseThrow(() -> new RuntimeException("Appointment not found for payment intent"));

            recordCapture(appointment);

            return PaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .status(paymentIntent.getStatus())
                    .amount(paymentIntent.getAmount())
                    .currency(paymentIntent.getCurrency())
                    .message("Payment captured successfully")
                    .appointmentId(appointment.getId())
                    .build();

        } catch (StripeException e) {
            log.error("Error capturing payment: {}", e.getMessage(), e);
            throw new org.example.backendbraiding.exception.PaymentProcessingException("Payment capture failed: " + e.getMessage());
        }
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse cancelPayment(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId).cancel();

            Appointment appointment = appointmentRepository.findByPaymentIntentId(paymentIntentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found for payment intent"));

            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
            appointmentRepository.save(appointment);

            return PaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .status(paymentIntent.getStatus())
                    .amount(paymentIntent.getAmount())
                    .currency(paymentIntent.getCurrency())
                    .message("Payment authorization cancelled successfully")
                    .appointmentId(appointment.getId())
                    .build();

        } catch (StripeException e) {
            log.error("Error cancelling payment: {}", e.getMessage(), e);
            throw new org.example.backendbraiding.exception.PaymentProcessingException("Payment authorization release failed: " + e.getMessage());
        }
    }

    @Transactional
    public PaymentIntentResponse getPaymentStatus(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            Appointment appointment = appointmentRepository.findByPaymentIntentId(paymentIntentId)
                    .orElse(null);

            return PaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .status(paymentIntent.getStatus())
                    .amount(paymentIntent.getAmount())
                    .currency(paymentIntent.getCurrency())
                    .message("Payment status retrieved successfully")
                    .appointmentId(appointment != null ? appointment.getId() : null)
                    .build();

        } catch (StripeException e) {
            log.error("Error retrieving payment status: {}", e.getMessage(), e);
            throw new org.example.backendbraiding.exception.PaymentProcessingException("Payment status lookup failed: " + e.getMessage());
        }
    }

    @Transactional
    public PaymentIntentResponse getBookingPaymentStatus(Long appointmentId, String paymentToken) {
        if (!bookingPaymentTokenService.isValidForAppointment(paymentToken, appointmentId)) {
            throw new IllegalArgumentException("Invalid or expired payment token");
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (appointment.getPaymentIntentId() == null) {
            throw new IllegalStateException("Payment has not been initialized");
        }
        return getPaymentStatus(appointment.getPaymentIntentId());
    }

    @Transactional
    public void synchronizePaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            Appointment appointment = appointmentRepository.findByPaymentIntentId(paymentIntentId)
                    .orElseThrow(() -> new IllegalStateException("Appointment not found for payment intent"));
            switch (intent.getStatus()) {
                case "requires_capture" -> recordAuthorization(appointment);
                case "succeeded" -> recordCapture(appointment);
                case "canceled" -> {
                    if (appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
                        appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
                        appointment.setPaymentAuthorizationExpiresAt(null);
                        if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
                        }
                        appointmentRepository.save(appointment);
                    }
                }
                case "requires_payment_method" -> {
                    // A declined attempt remains retryable on the same PaymentIntent.
                    if (appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED
                            && appointment.getPaymentStatus() != Appointment.PaymentStatus.AUTHORIZED) {
                        appointment.setPaymentStatus(Appointment.PaymentStatus.PENDING);
                        appointmentRepository.save(appointment);
                    }
                }
                default -> log.debug("No local payment transition for Stripe status {}", intent.getStatus());
            }
        } catch (StripeException e) {
            throw new org.example.backendbraiding.exception.PaymentProcessingException(
                    "Payment status synchronization failed: " + e.getMessage());
        }
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void markCaptureFailed(String paymentIntentId, String reason) {
        appointmentRepository.findByPaymentIntentId(paymentIntentId).ifPresent(appointment -> {
            appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURE_FAILED);
            appointment.setAdminNotes("Payment capture failed; retry required: " + reason);
            appointmentRepository.save(appointment);
        });
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void markCancellationFailed(String paymentIntentId, String reason) {
        appointmentRepository.findByPaymentIntentId(paymentIntentId).ifPresent(appointment -> {
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLATION_FAILED);
            appointment.setAdminNotes("Payment authorization release failed; retry required: " + reason);
            appointmentRepository.save(appointment);
        });
    }

    @Scheduled(fixedDelayString = "${stripe.reconciliation.interval-ms:300000}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void reconcilePaymentStates() {
        for (Appointment appointment : appointmentRepository.findAppointmentsNeedingPaymentReconciliation()) {
            try {
                synchronizePaymentIntent(appointment.getPaymentIntentId());
            } catch (RuntimeException e) {
                log.warn("Could not reconcile payment {} for appointment {}: {}",
                        appointment.getPaymentIntentId(), appointment.getId(), e.getMessage());
            }
        }
    }

}
