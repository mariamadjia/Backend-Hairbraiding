package org.example.backendbraiding.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.CustomerCreateParams;
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

    private final AppointmentRepository appointmentRepository;
    private final org.example.backendbraiding.repository.AppointmentSettingsRepository appointmentSettingsRepository;
    private final BookingPaymentTokenService bookingPaymentTokenService;
    private final NotificationOutboxService notificationOutboxService;
    private final AppointmentEventService appointmentEventService;
    private final AppointmentNotificationTemplates notificationTemplates;

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
                if (PaymentLifecycleRules.isReusableForConfirmation(existingIntent.getStatus())) {
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment intent ready for authorization.");
                }
                if ("requires_capture".equals(existingIntent.getStatus())) {
                    recordAuthorization(appointment, existingIntent);
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment is already authorized.");
                }
                if ("succeeded".equals(existingIntent.getStatus())) {
                    recordCapture(appointment, existingIntent);
                    return paymentIntentResponse(existingIntent, appointment.getId(), "Payment is already complete.");
                }
                replacedIntentId = existingIntent.getId();
                appointment.setPaymentIntentId(null);
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("appointmentId", appointment.getId().toString());
            metadata.put("customerEmail", appointment.getCustomer().getEmail());
            metadata.put("customerName", appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName());

            Long quotedDepositCents = appointment.getDepositAmount();
            if (quotedDepositCents == null) {
                throw new IllegalStateException("This booking is missing its deposit quote. Please start the booking again.");
            }
            long depositAmountCents = calculateDepositAmountCents(appointment.getPrice(), quotedDepositCents);

            String stripeCustomerId = ensureStripeCustomer(appointment);

            PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(depositAmountCents)
                    .setCurrency("usd")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .setCustomer(stripeCustomerId)
                    .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                    // A reusable card is required for the explicitly accepted no-show policy.
                    .addPaymentMethodType("card")
                    .putAllMetadata(metadata)
                    .build(), RequestOptions.builder()
                    .setIdempotencyKey(replacedIntentId == null
                            ? "booking-payment-intent-dynamic-v3-" + appointment.getId()
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

    private void recordAuthorization(Appointment appointment, PaymentIntent intent) {
        boolean firstAuthorization = appointment.getPaymentStatus() != Appointment.PaymentStatus.AUTHORIZED;
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setAmountAuthorized(intent.getAmountCapturable() != null && intent.getAmountCapturable() > 0
                ? intent.getAmountCapturable() : intent.getAmount());
        appointment.setPaymentPendingExpiresAt(null);
        // Stripe authorization windows vary by method. Six days is a conservative
        // operational deadline for the shortest commonly enabled methods.
        appointment.setPaymentAuthorizationExpiresAt(LocalDateTime.now().plusDays(6));
        appointmentRepository.save(appointment);
        boolean requiresAdminApproval = appointmentSettingsRepository.findFirstByOrderByIdDesc()
                .map(settings -> settings.getRequireApproval())
                .orElse(true);
        if (firstAuthorization && requiresAdminApproval) {
            AppointmentNotificationTemplates.Notification notification = notificationTemplates.pending(appointment);
            notificationOutboxService.enqueueEmail(appointment, notification.subject(), notification.emailBody());
        }
    }

    private void recordCapture(Appointment appointment, PaymentIntent intent) {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
        appointment.setAmountAuthorized(intent.getAmount());
        appointment.setAmountCaptured(intent.getAmountReceived());
        appointment.setPaymentCapturedAt(LocalDateTime.now());
        appointment.setPaymentAuthorizationExpiresAt(null);
        recordPaymentMethod(appointment, intent);
        boolean notifyApproval = appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                && appointment.getApprovedAt() != null;
        if (notifyApproval) {
            appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
        }
        appointmentRepository.save(appointment);
        if (notifyApproval) {
            appointmentEventService.record(appointment, "APPROVED", appointment.getApprovedBy(), null);
            AppointmentNotificationTemplates.Notification notification = notificationTemplates.approved(appointment);
            notificationOutboxService.enqueueEmail(appointment, notification.subject(), notification.emailBody());
            notificationOutboxService.enqueueSms(appointment, notification.smsBody());
        }
    }

    private void recordPaymentMethod(Appointment appointment, PaymentIntent intent) {
        if (intent.getPaymentMethod() == null || intent.getPaymentMethod().isBlank()) return;
        try {
            PaymentMethod method = PaymentMethod.retrieve(intent.getPaymentMethod());
            if (method.getCard() != null) {
                appointment.setPaymentMethodBrand(method.getCard().getBrand());
                appointment.setPaymentMethodLast4(method.getCard().getLast4());
                appointment.getCustomer().setStripePaymentMethodId(method.getId());
            }
        } catch (StripeException exception) {
            log.warn("Payment {} completed but payment-method details could not be loaded: {}",
                    intent.getId(), exception.getMessage());
        }
    }

    private String ensureStripeCustomer(Appointment appointment) throws StripeException {
        org.example.backendbraiding.model.Customer customer = appointment.getCustomer();
        if (customer.getStripeCustomerId() != null && !customer.getStripeCustomerId().isBlank()) {
            return customer.getStripeCustomerId();
        }
        com.stripe.model.Customer stripeCustomer = com.stripe.model.Customer.create(CustomerCreateParams.builder()
                .setEmail(customer.getEmail())
                .setName(customer.getFirstName() + " " + customer.getLastName())
                .setPhone(customer.getPhoneNumber())
                .putMetadata("localCustomerId", customer.getId().toString())
                .build(), RequestOptions.builder()
                .setIdempotencyKey("booking-stripe-customer-v1-" + customer.getId())
                .build());
        customer.setStripeCustomerId(stripeCustomer.getId());
        return stripeCustomer.getId();
    }

    private long calculateDepositAmountCents(String appointmentPrice, long configuredDepositCents) {
        if (configuredDepositCents <= 0) throw new IllegalStateException("Configured deposit must be greater than zero");
        if (appointmentPrice == null || appointmentPrice.isBlank()) {
            return configuredDepositCents;
        }
        try {
            BigDecimal price = new BigDecimal(appointmentPrice.replaceAll("[^0-9.]", ""))
                    .setScale(2, RoundingMode.HALF_UP);
            long priceCents = price.movePointRight(2).longValueExact();
            if (priceCents <= 0) {
                throw new IllegalArgumentException("Appointment price must be greater than zero");
            }
            return Math.min(configuredDepositCents, priceCents);
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
            Appointment appointment = appointmentRepository.findByPaymentIntentId(request.getPaymentIntentId())
                    .orElseThrow(() -> new IllegalArgumentException("Appointment not found for payment intent"));
            PaymentIntent current = PaymentIntent.retrieve(request.getPaymentIntentId());
            if ("succeeded".equals(current.getStatus())) {
                recordCapture(appointment, current);
                return paymentIntentResponse(current, appointment.getId(), "Payment was already captured.");
            }
            if (!"requires_capture".equals(current.getStatus())) {
                throw new IllegalStateException("Payment is not ready for capture");
            }
            long fullAmount = current.getAmountCapturable();
            if (!PaymentLifecycleRules.isFullCapture(request.getAmountToCapture(), fullAmount)) {
                throw new IllegalArgumentException("Partial capture is not supported; capture the full authorized deposit");
            }
            PaymentIntentCaptureParams params = PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(fullAmount)
                    .build();
            PaymentIntent paymentIntent = current.capture(params, RequestOptions.builder()
                    .setIdempotencyKey("booking-capture-v1-" + appointment.getId())
                    .build());

            recordCapture(appointment, paymentIntent);

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
                case "requires_capture" -> recordAuthorization(appointment, intent);
                case "succeeded" -> recordCapture(appointment, intent);
                case "canceled" -> {
                    if (appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
                        appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
                        appointment.setPaymentAuthorizationExpiresAt(null);
                        if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
                        }
                        appointmentRepository.save(appointment);
                        appointmentEventService.record(appointment, "PAYMENT_CANCELLED", null, null);
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
            appointmentEventService.record(appointment, "PAYMENT_CAPTURE_FAILED", appointment.getApprovedBy(), reason);
        });
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void markCancellationFailed(String paymentIntentId, String reason) {
        appointmentRepository.findByPaymentIntentId(paymentIntentId).ifPresent(appointment -> {
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLATION_FAILED);
            appointment.setAdminNotes("Payment authorization release failed; retry required: " + reason);
            appointmentRepository.save(appointment);
            appointmentEventService.record(appointment, "PAYMENT_CANCELLATION_FAILED", appointment.getApprovedBy(), reason);
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

    @Scheduled(fixedDelayString = "${stripe.authorization-expiry.interval-ms:60000}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void releaseExpiredAuthorizations() {
        for (Appointment appointment : appointmentRepository.findExpiredAuthorizations(LocalDateTime.now())) {
            try {
                cancelPayment(appointment.getPaymentIntentId());
                if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                    appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
                }
                appointment.setAdminNotes("Automatically cancelled: payment authorization expired");
                appointmentRepository.save(appointment);
                appointmentEventService.record(appointment, "AUTHORIZATION_EXPIRED", null, appointment.getAdminNotes());
            } catch (RuntimeException exception) {
                markCancellationFailed(appointment.getPaymentIntentId(), exception.getMessage());
                log.warn("Could not release expired authorization {}: {}",
                        appointment.getPaymentIntentId(), exception.getMessage());
            }
        }
    }

}
