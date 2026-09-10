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
import org.springframework.transaction.annotation.Propagation;

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
    private final BookingPaymentTokenService bookingPaymentTokenService;
    private final NotificationOutboxService notificationOutboxService;
    private final AppointmentEventService appointmentEventService;
    private final AppointmentNotificationTemplates notificationTemplates;
    private final AppointmentManagementTokenService managementTokenService;
    private final OwnerDepositTokenService ownerDepositTokenService;

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        if (!bookingPaymentTokenService.isValidForAppointment(request.getPaymentToken(), request.getAppointmentId())) {
            throw new IllegalArgumentException("Invalid or expired payment token");
        }

        Appointment appointment = appointmentRepository.findByIdForUpdate(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

        if (appointment.getPaymentStatus() == Appointment.PaymentStatus.NOT_REQUIRED) {
            return noPaymentRequiredResponse(appointment);
        }
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
                appointment.setAmountAuthorized(null);
                appointment.setPaymentAuthorizationExpiresAt(null);
                appointment.setApprovedAt(null);
                appointment.setApprovedBy(null);
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

            if (depositAmountCents == 0) {
                return completeWithoutPayment(appointment);
            }

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

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse createOwnerDepositIntent(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (appointment.getBookingSource() != Appointment.BookingSource.OWNER
                || !Boolean.TRUE.equals(appointment.getDepositRequired())) {
            throw new IllegalStateException("This appointment does not require an owner-sent deposit");
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only an awaiting-deposit appointment can create a payment");
        }
        if (appointment.getDepositLinkExpiresAt() == null
                || !appointment.getDepositLinkExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("The deposit link has expired");
        }
        try {
            if (appointment.getPaymentIntentId() != null) {
                PaymentIntent existing = PaymentIntent.retrieve(appointment.getPaymentIntentId());
                if (PaymentLifecycleRules.isReusableForConfirmation(existing.getStatus())) {
                    return paymentIntentResponse(existing, appointmentId, "Deposit payment is ready.");
                }
                if ("succeeded".equals(existing.getStatus())) {
                    recordCapture(appointment, existing);
                    return paymentIntentResponse(existing, appointmentId, "Deposit is already paid.");
                }
            }
            long amount = calculateDepositAmountCents(appointment.getPrice(), appointment.getDepositAmount());
            Map<String, String> metadata = new HashMap<>();
            metadata.put("appointmentId", appointmentId.toString());
            metadata.put("bookingSource", "OWNER");
                PaymentIntent intent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency("usd")
                    .setCustomer(ensureStripeCustomer(appointment))
                    .setReceiptEmail(appointment.getCustomer().getEmail())
                    .addPaymentMethodType("card")
                    .putAllMetadata(metadata)
                    .build(), RequestOptions.builder()
                    .setIdempotencyKey("owner-deposit-payment-v1-" + appointmentId)
                    .build());
            appointment.setPaymentIntentId(intent.getId());
            appointment.setDepositAmount(amount);
            appointment.setPaymentStatus(Appointment.PaymentStatus.PENDING);
            appointmentRepository.save(appointment);
            return paymentIntentResponse(intent, appointmentId, "Deposit payment is ready.");
        } catch (StripeException exception) {
            throw new org.example.backendbraiding.exception.PaymentProcessingException(
                    "Payment provider could not create the deposit payment");
        }
    }

    @Transactional
    public PaymentIntentResponse getOwnerDepositIntent(Long appointmentId, String token) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (!ownerDepositTokenService.isValid(token, appointmentId, appointment.getOwnerDepositTokenHash())) {
            throw new IllegalArgumentException("Invalid or expired deposit link");
        }
        if (appointment.getBookingSource() != Appointment.BookingSource.OWNER
                || !Boolean.TRUE.equals(appointment.getDepositRequired())) {
            throw new IllegalStateException("This appointment does not require a deposit");
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING
                && appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
            throw new IllegalStateException("This appointment is no longer awaiting payment");
        }
        if (appointment.getPaymentIntentId() == null) {
            throw new IllegalStateException("Deposit payment has not been initialized");
        }
        if (appointment.getDepositLinkExpiresAt() != null
                && !appointment.getDepositLinkExpiresAt().isAfter(LocalDateTime.now())
                && appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
            throw new IllegalStateException("The deposit link has expired");
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(appointment.getPaymentIntentId());
            if ("succeeded".equals(intent.getStatus())
                    && appointment.getPaymentStatus() != Appointment.PaymentStatus.CAPTURED) {
                recordCapture(appointment, intent);
            }
            return paymentIntentResponse(intent, appointmentId,
                    appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED
                            ? "Deposit is already paid." : "Deposit payment is ready.");
        } catch (StripeException exception) {
            throw new org.example.backendbraiding.exception.PaymentProcessingException(
                    "Payment status could not be loaded");
        }
    }

    private void recordAuthorization(Appointment appointment, PaymentIntent intent) {
        boolean firstAuthorization = appointment.getAmountAuthorized() == null;
        boolean operationFailed = appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURE_FAILED
                || appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLATION_FAILED;
        if (!operationFailed) appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
        appointment.setAmountAuthorized(intent.getAmountCapturable() != null && intent.getAmountCapturable() > 0
                ? intent.getAmountCapturable() : intent.getAmount());
        appointment.setPaymentPendingExpiresAt(null);
        // Stripe authorization windows vary by method. Six days is a conservative
        // operational deadline for the shortest commonly enabled methods.
        if (appointment.getPaymentAuthorizationExpiresAt() == null) {
            appointment.setPaymentAuthorizationExpiresAt(LocalDateTime.now().plusDays(6));
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING
                || appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLATION_FAILED) {
            appointmentRepository.save(appointment);
            return;
        }
        if (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now(java.time.ZoneId.of("America/Chicago")))
                || PaymentLifecycleRules.isAuthorizationExpired(appointment.getPaymentAuthorizationExpiresAt(), LocalDateTime.now())) {
            appointmentRepository.save(appointment);
            return;
        }
        if (Boolean.FALSE.equals(appointment.getRequireApproval()) && appointment.getApprovedAt() == null) {
            appointment.setApprovedAt(LocalDateTime.now(java.time.ZoneId.of("America/Chicago")));
            appointmentEventService.record(appointment, "AUTO_APPROVAL_REQUESTED", null, null);
        }
        appointmentRepository.save(appointment);
        boolean requiresAdminApproval = !Boolean.FALSE.equals(appointment.getRequireApproval());
        if (firstAuthorization) {
            AppointmentNotificationTemplates.Notification salonNotification = notificationTemplates.adminNewBooking(appointment);
            if (requiresAdminApproval) {
                AppointmentNotificationTemplates.Notification customerNotification = notificationTemplates.pending(appointment);
                notificationOutboxService.enqueueCustomerAndSalon(appointment,
                        customerNotification.subject(), customerNotification.emailBody(),
                        customerNotification.smsBody(),
                        salonNotification.subject(), salonNotification.emailBody(), salonNotification.smsBody());
            } else {
                notificationOutboxService.enqueueSalonNotifications(appointment,
                        salonNotification.subject(), salonNotification.emailBody(), salonNotification.smsBody());
            }
        }
    }

    private void recordCapture(Appointment appointment, PaymentIntent intent) {
        appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
        appointment.setAmountAuthorized(intent.getAmount());
        appointment.setAmountCaptured(intent.getAmountReceived());
        if (appointment.getPaymentCapturedAt() == null) appointment.setPaymentCapturedAt(LocalDateTime.now());
        appointment.setPaymentAuthorizationExpiresAt(null);
        recordPaymentMethod(appointment, intent);
        boolean ownerDeposit = appointment.getBookingSource() == Appointment.BookingSource.OWNER;
        boolean notifyApproval = appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                && (appointment.getApprovedAt() != null || ownerDeposit);
        if (notifyApproval) {
            appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
            if (ownerDeposit && appointment.getApprovedAt() == null) {
                appointment.setApprovedBy(appointment.getCreatedByAdmin());
                appointment.setApprovedAt(LocalDateTime.now());
            }
        }
        appointmentRepository.save(appointment);
        if (notifyApproval) {
            appointmentEventService.record(appointment, "APPROVED", appointment.getApprovedBy(), null);
            String managementUrl = managementTokenService.issue(appointment);
            AppointmentNotificationTemplates.Notification notification = ownerDeposit
                    ? notificationTemplates.ownerDepositPaid(appointment, managementUrl)
                    : notificationTemplates.approved(appointment, managementUrl);
            notificationOutboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
        }
    }

    private void recordPaymentMethod(Appointment appointment, PaymentIntent intent) {
        if (intent.getPaymentMethod() == null || intent.getPaymentMethod().isBlank()) return;
        try {
            PaymentMethod method = PaymentMethod.retrieve(intent.getPaymentMethod());
            if (method.getCard() != null) {
                appointment.setPaymentMethodBrand(method.getCard().getBrand());
                appointment.setPaymentMethodLast4(method.getCard().getLast4());
            } else {
                log.warn("Payment {} used unsupported reusable payment method type {}",
                        intent.getId(), method.getType());
                return;
            }
            if (appointment.getBookingSource() != Appointment.BookingSource.OWNER) {
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
        if (configuredDepositCents < 0) throw new IllegalStateException("Configured deposit cannot be negative");
        if (configuredDepositCents == 0) return 0;
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

    private PaymentIntentResponse completeWithoutPayment(Appointment appointment) {
        appointment.setDepositAmount(0L);
        appointment.setAmountAuthorized(0L);
        appointment.setAmountCaptured(0L);
        appointment.setPaymentStatus(Appointment.PaymentStatus.NOT_REQUIRED);
        appointment.setPaymentPendingExpiresAt(null);
        boolean requiresApproval = !Boolean.FALSE.equals(appointment.getRequireApproval());
        if (!requiresApproval) {
            appointment.setStatus(Appointment.AppointmentStatus.APPROVED);
            appointment.setApprovedAt(LocalDateTime.now(java.time.ZoneId.of("America/Chicago")));
        }
        appointmentRepository.save(appointment);
        appointmentEventService.record(appointment, "PAYMENT_NOT_REQUIRED", null, null);

        AppointmentNotificationTemplates.Notification customerNotification;
        if (requiresApproval) {
            customerNotification = notificationTemplates.pendingWithoutDeposit(appointment);
        } else {
            appointmentEventService.record(appointment, "APPROVED", null, "No deposit required");
            customerNotification = notificationTemplates.approvedWithoutDeposit(
                    appointment, managementTokenService.issue(appointment));
        }
        AppointmentNotificationTemplates.Notification salonNotification = notificationTemplates.adminNewBooking(appointment);
        notificationOutboxService.enqueueCustomerAndSalon(appointment,
                customerNotification.subject(), customerNotification.emailBody(), customerNotification.smsBody(),
                salonNotification.subject(), salonNotification.emailBody(), salonNotification.smsBody());

        return noPaymentRequiredResponse(appointment);
    }

    private PaymentIntentResponse noPaymentRequiredResponse(Appointment appointment) {
        boolean requiresApproval = !Boolean.FALSE.equals(appointment.getRequireApproval());
        return PaymentIntentResponse.builder()
                .status("not_required")
                .amount(0L)
                .currency("usd")
                .message(requiresApproval
                        ? "No deposit is required; the appointment is awaiting salon approval."
                        : "No deposit is required; the appointment is confirmed.")
                .appointmentId(appointment.getId())
                .appointmentStatus(appointment.getStatus().name())
                .paymentStatus(appointment.getPaymentStatus().name())
                .requireApproval(appointment.getRequireApproval())
                .approvalRequested(appointment.getApprovedAt() != null)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse capturePayment(PaymentCaptureRequest request) {
        try {
            Appointment appointment = appointmentRepository.findByPaymentIntentIdForUpdate(request.getPaymentIntentId())
                    .orElseThrow(() -> new IllegalArgumentException("Appointment not found for payment intent"));
            if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING
                    && !(appointment.getStatus() == Appointment.AppointmentStatus.APPROVED
                    && appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED)) {
                throw new IllegalStateException("Only an approved capture request may be charged");
            }
            if (appointment.getApprovedAt() == null) {
                throw new IllegalStateException("Approve the appointment before capturing its deposit");
            }
            PaymentIntent current = PaymentIntent.retrieve(request.getPaymentIntentId());
            if ("succeeded".equals(current.getStatus())) {
                recordCapture(appointment, current);
                return paymentIntentResponse(current, appointment.getId(), "Payment was already captured.");
            }
            if (!"requires_capture".equals(current.getStatus())) {
                throw new IllegalStateException("Payment is not ready for capture");
            }
            if (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now(java.time.ZoneId.of("America/Chicago")))
                    || PaymentLifecycleRules.isAuthorizationExpired(appointment.getPaymentAuthorizationExpiresAt(), LocalDateTime.now())) {
                throw new IllegalStateException("Cannot capture an expired or past appointment");
            }
            long fullAmount = current.getAmountCapturable();
            if (!PaymentLifecycleRules.isFullCapture(request.getAmountToCapture(), fullAmount)) {
                throw new IllegalArgumentException("Partial capture is not supported; capture the full authorized deposit");
            }
            PaymentIntentCaptureParams params = PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(fullAmount)
                    .build();
            PaymentIntent paymentIntent = current.capture(params, RequestOptions.builder()
                    .setIdempotencyKey("booking-capture-v1-" + appointment.getId() + "-" + request.getPaymentIntentId())
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public PaymentIntentResponse cancelPayment(String paymentIntentId) {
        try {
            Appointment appointment = appointmentRepository.findByPaymentIntentIdForUpdate(paymentIntentId)
                    .orElseThrow(() -> new IllegalArgumentException("Appointment not found for payment intent"));
            boolean expired = appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                    && (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now(java.time.ZoneId.of("America/Chicago")))
                    || (appointment.getPaymentAuthorizationExpiresAt() != null
                    && PaymentLifecycleRules.isAuthorizationExpired(appointment.getPaymentAuthorizationExpiresAt(), LocalDateTime.now()))
                    || (appointment.getPaymentPendingExpiresAt() != null
                    && !appointment.getPaymentPendingExpiresAt().isAfter(LocalDateTime.now())));
            if (appointment.getStatus() == Appointment.AppointmentStatus.APPROVED
                    || (appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                    && appointment.getApprovedAt() != null && !expired)) {
                throw new IllegalStateException("Cannot release payment while approval is processing or confirmed");
            }
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            if (!"canceled".equals(paymentIntent.getStatus())) paymentIntent = paymentIntent.cancel();
            appointment.setPaymentAuthorizationExpiresAt(null);
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
            if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            }
            appointmentRepository.save(appointment);
            if (expired) {
                appointmentEventService.record(appointment, "AUTHORIZATION_EXPIRED", null, null);
                var notification = notificationTemplates.expired(appointment);
                notificationOutboxService.enqueueBoth(appointment, notification.subject(), notification.emailBody(), notification.smsBody());
            }

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
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.NOT_REQUIRED) {
                return PaymentIntentResponse.builder()
                        .status("not_required")
                        .amount(0L)
                        .currency("usd")
                        .message("No payment is required for this appointment.")
                        .appointmentId(appointment.getId())
                        .appointmentStatus(appointment.getStatus().name())
                        .paymentStatus(appointment.getPaymentStatus().name())
                        .requireApproval(appointment.getRequireApproval())
                        .approvalRequested(appointment.getApprovedAt() != null)
                        .build();
            }
            throw new IllegalStateException("Payment has not been initialized");
        }
        PaymentIntentResponse response = getPaymentStatus(appointment.getPaymentIntentId());
        response.setAppointmentStatus(appointment.getStatus().name());
        response.setPaymentStatus(appointment.getPaymentStatus().name());
        response.setRequireApproval(appointment.getRequireApproval());
        response.setApprovalRequested(appointment.getApprovedAt() != null);
        return response;
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void synchronizePaymentIntent(String paymentIntentId) {
        try {
            Appointment appointment = appointmentRepository.findByPaymentIntentIdForUpdate(paymentIntentId)
                    .orElseThrow(() -> new IllegalStateException("Appointment not found for payment intent"));
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
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
                    if (appointment.getPaymentStatus() == Appointment.PaymentStatus.PENDING
                            || appointment.getPaymentStatus() == Appointment.PaymentStatus.FAILED) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void markCaptureFailed(String paymentIntentId, String reason) {
        appointmentRepository.findByPaymentIntentIdForUpdate(paymentIntentId).ifPresent(appointment -> {
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED) return;
            appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURE_FAILED);
            // Preserve the administrator's notes; error details belong in the audit event.
            appointmentRepository.save(appointment);
            appointmentEventService.record(appointment, "PAYMENT_CAPTURE_FAILED", appointment.getApprovedBy(), reason);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public void markCancellationFailed(String paymentIntentId, String reason) {
        appointmentRepository.findByPaymentIntentIdForUpdate(paymentIntentId).ifPresent(appointment -> {
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED
                    || appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLED) return;
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLATION_FAILED);
            appointmentRepository.save(appointment);
            appointmentEventService.record(appointment, "PAYMENT_CANCELLATION_FAILED", appointment.getApprovedBy(), reason);
        });
    }

}
