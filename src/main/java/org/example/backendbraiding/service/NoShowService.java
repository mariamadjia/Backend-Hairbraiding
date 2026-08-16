package org.example.backendbraiding.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.NoShowChargeRequest;
import org.example.backendbraiding.dto.NoShowFeeDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.model.NoShowFee;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.NoShowFeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoShowService {
    static final int GRACE_MINUTES = 30;
    static final int NORMAL_WINDOW_HOURS = 24;
    static final int AUTOMATIC_WINDOW_DAYS = 7;
    static final int FEE_RATE_PERCENT = 60;
    private static final ZoneId SALON_ZONE = ZoneId.of("America/Chicago");

    private final AppointmentRepository appointmentRepository;
    private final NoShowFeeRepository noShowFeeRepository;
    private final AdminRepository adminRepository;
    private final AppointmentEventService appointmentEventService;
    private final NotificationOutboxService notificationOutboxService;
    private final AppointmentNotificationTemplates notificationTemplates;
    private final TransactionTemplate transactionTemplate;

    public NoShowFeeDTO markAndCharge(Long appointmentId, Long adminId, NoShowChargeRequest request) {
        NoShowFee fee = transactionTemplate.execute(status -> prepare(appointmentId, adminId, request));
        if (fee == null) throw new IllegalStateException("Could not prepare no-show charge");
        if (fee.getPaymentStatus() == NoShowFee.PaymentStatus.PAID
                || fee.getPaymentStatus() == NoShowFee.PaymentStatus.PROCESSING) {
            return toDto(fee);
        }
        if (fee.getAmountToChargeCents() == 0) {
            return toDto(transactionTemplate.execute(status -> markPaidWithoutCharge(fee.getId())));
        }
        return charge(fee);
    }

    private NoShowFee prepare(Long appointmentId, Long adminId, NoShowChargeRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Appointment not found"));
        NoShowFee existing = noShowFeeRepository.findByAppointmentId(appointmentId).orElse(null);
        if (existing != null) return existing;
        if (appointment.getStatus() != Appointment.AppointmentStatus.APPROVED) {
            throw new IllegalStateException("Only an approved appointment can be marked as a no-show");
        }
        LocalDateTime now = salonNow();
        LocalDateTime eligibleAt = appointment.getAppointmentDateTime().plusMinutes(GRACE_MINUTES);
        LocalDateTime normalDeadline = appointment.getAppointmentDateTime().plusHours(NORMAL_WINDOW_HOURS);
        LocalDateTime hardDeadline = appointment.getAppointmentDateTime().plusDays(AUTOMATIC_WINDOW_DAYS);
        if (now.isBefore(eligibleAt)) {
            throw new IllegalStateException("No-show charging becomes available after the 30-minute grace period");
        }
        if (now.isAfter(hardDeadline)) {
            throw new IllegalStateException("The seven-day automatic no-show charge window has expired");
        }
        if (now.isAfter(normalDeadline) && !request.isConfirmOverdue()) {
            throw new IllegalStateException("This charge is outside the normal 24-hour period and requires overdue confirmation");
        }
        if (appointment.getCustomer().getStripeCustomerId() == null
                || appointment.getCustomer().getStripePaymentMethodId() == null
                || appointment.getOffSessionConsentAt() == null) {
            throw new IllegalStateException("This appointment does not have a saved card with off-session consent");
        }

        long servicePrice = MoneySupport.requirePositiveCents(appointment.getPrice(), "Scheduled service price");
        long totalFee = BigDecimal.valueOf(servicePrice)
                .multiply(BigDecimal.valueOf(FEE_RATE_PERCENT))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        long depositCredit = Math.min(totalFee, Math.max(0, appointment.getAmountCaptured() == null
                ? 0 : appointment.getAmountCaptured()));

        NoShowFee fee = new NoShowFee();
        fee.setAppointment(appointment);
        fee.setScheduledServicePriceCents(servicePrice);
        fee.setFeeRatePercent(FEE_RATE_PERCENT);
        fee.setTotalFeeCents(totalFee);
        fee.setDepositCreditCents(depositCredit);
        fee.setAmountToChargeCents(Math.max(0, totalFee - depositCredit));
        fee.setMarkedAt(now);
        fee.setAdminNote(clean(request.getAdminNote()));
        fee.setPaymentStatus(NoShowFee.PaymentStatus.UNPAID);
        fee = noShowFeeRepository.save(fee);

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new org.example.backendbraiding.exception.ResourceNotFoundException("Administrator not found"));
        appointment.setStatus(Appointment.AppointmentStatus.NO_SHOW);
        appointment.setNoShowMarkedAt(now);
        appointment.setNoShowMarkedBy(admin);
        appointmentRepository.save(appointment);
        appointmentEventService.record(appointment, "MARKED_NO_SHOW", admin,
                "60% fee; deposit credited; remaining charge " + fee.getAmountToChargeCents() + " cents");
        return fee;
    }

    private NoShowFeeDTO charge(NoShowFee fee) {
        NoShowFee attempt = transactionTemplate.execute(status -> beginAttempt(fee.getId()));
        if (attempt == null) throw new IllegalStateException("Could not begin no-show charge attempt");
        Appointment appointment = attempt.getAppointment();
        try {
            PaymentIntent intent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(attempt.getAmountToChargeCents())
                    .setCurrency("usd")
                    .setCustomer(appointment.getCustomer().getStripeCustomerId())
                    .setPaymentMethod(appointment.getCustomer().getStripePaymentMethodId())
                    .setOffSession(true)
                    .setConfirm(true)
                    .addPaymentMethodType("card")
                    .putAllMetadata(Map.of(
                            "appointmentId", appointment.getId().toString(),
                            "paymentPurpose", "no_show_fee",
                            "depositCreditCents", attempt.getDepositCreditCents().toString()))
                    .build(), RequestOptions.builder()
                    .setIdempotencyKey("no-show-charge-v1-" + appointment.getId() + "-attempt-" + attempt.getChargeAttemptCount())
                    .build());
            NoShowFee saved = transactionTemplate.execute(status -> applyIntent(attempt.getId(), intent));
            return toDto(saved);
        } catch (StripeException exception) {
            NoShowFee saved = transactionTemplate.execute(status -> markFailed(attempt.getId(), exception.getMessage()));
            return toDto(saved);
        }
    }

    private NoShowFee beginAttempt(Long feeId) {
        NoShowFee fee = noShowFeeRepository.findByIdForUpdate(feeId).orElseThrow();
        if (fee.getPaymentStatus() == NoShowFee.PaymentStatus.PAID) return fee;
        fee.setChargeAttemptCount((fee.getChargeAttemptCount() == null ? 0 : fee.getChargeAttemptCount()) + 1);
        fee.setPaymentStatus(NoShowFee.PaymentStatus.PROCESSING);
        fee.setFailureMessage(null);
        fee.setStripePaymentIntentId(null);
        fee.setChargeAttemptedAt(salonNow());
        return noShowFeeRepository.save(fee);
    }

    public boolean synchronize(String paymentIntentId) {
        NoShowFee fee = noShowFeeRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (fee == null) return false;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            transactionTemplate.executeWithoutResult(status -> applyIntent(fee.getId(), intent));
            return true;
        } catch (StripeException exception) {
            throw new org.example.backendbraiding.exception.PaymentProcessingException(
                    "No-show payment synchronization failed: " + exception.getMessage());
        }
    }

    private NoShowFee applyIntent(Long feeId, PaymentIntent intent) {
        NoShowFee fee = noShowFeeRepository.findById(feeId).orElseThrow();
        NoShowFee.PaymentStatus previousStatus = fee.getPaymentStatus();
        fee.setStripePaymentIntentId(intent.getId());
        fee.setChargeAttemptedAt(salonNow());
        fee.setFailureMessage(null);
        switch (intent.getStatus()) {
            case "succeeded" -> {
                fee.setPaymentStatus(NoShowFee.PaymentStatus.PAID);
                fee.setPaidAt(salonNow());
            }
            case "processing" -> fee.setPaymentStatus(NoShowFee.PaymentStatus.PROCESSING);
            default -> {
                fee.setPaymentStatus(NoShowFee.PaymentStatus.FAILED);
                fee.setFailureMessage(intent.getLastPaymentError() == null
                        ? "The saved card could not be charged" : intent.getLastPaymentError().getMessage());
            }
        }
        NoShowFee saved = noShowFeeRepository.save(fee);
        if (saved.getPaymentStatus() != previousStatus
                && (saved.getPaymentStatus() == NoShowFee.PaymentStatus.PAID
                || saved.getPaymentStatus() == NoShowFee.PaymentStatus.FAILED)) {
            enqueueResult(saved);
        }
        return saved;
    }

    private NoShowFee markFailed(Long feeId, String message) {
        NoShowFee fee = noShowFeeRepository.findById(feeId).orElseThrow();
        fee.setPaymentStatus(NoShowFee.PaymentStatus.FAILED);
        fee.setChargeAttemptedAt(salonNow());
        fee.setFailureMessage(message == null ? "The saved card could not be charged" : message.substring(0, Math.min(1000, message.length())));
        NoShowFee saved = noShowFeeRepository.save(fee);
        enqueueResult(saved);
        return saved;
    }

    private NoShowFee markPaidWithoutCharge(Long feeId) {
        NoShowFee fee = noShowFeeRepository.findById(feeId).orElseThrow();
        fee.setPaymentStatus(NoShowFee.PaymentStatus.PAID);
        fee.setPaidAt(salonNow());
        NoShowFee saved = noShowFeeRepository.save(fee);
        enqueueResult(saved);
        return saved;
    }

    private void enqueueResult(NoShowFee fee) {
        Appointment appointment = fee.getAppointment();
        if (fee.getPaymentStatus() == NoShowFee.PaymentStatus.PAID) {
            AppointmentNotificationTemplates.Notification notification = notificationTemplates.noShowPaid(
                    appointment, fee.getScheduledServicePriceCents(), fee.getTotalFeeCents(),
                    fee.getDepositCreditCents(), fee.getAmountToChargeCents());
            notificationOutboxService.enqueueEmail(appointment, notification.subject(), notification.emailBody());
        } else if (fee.getPaymentStatus() == NoShowFee.PaymentStatus.FAILED) {
            AppointmentNotificationTemplates.Notification notification = notificationTemplates.noShowFailed(
                    appointment, fee.getScheduledServicePriceCents(), fee.getTotalFeeCents(),
                    fee.getDepositCreditCents(), fee.getAmountToChargeCents());
            notificationOutboxService.enqueueEmail(appointment, notification.subject(), notification.emailBody());
        }
    }

    public NoShowFeeDTO toDto(NoShowFee fee) {
        Appointment appointment = fee.getAppointment();
        LocalDateTime now = salonNow();
        LocalDateTime normalDeadline = appointment.getAppointmentDateTime().plusHours(NORMAL_WINDOW_HOURS);
        LocalDateTime hardDeadline = appointment.getAppointmentDateTime().plusDays(AUTOMATIC_WINDOW_DAYS);
        return new NoShowFeeDTO(appointment.getId(), fee.getScheduledServicePriceCents(), fee.getFeeRatePercent(),
                fee.getTotalFeeCents(), fee.getDepositCreditCents(), fee.getAmountToChargeCents(),
                fee.getFeeDecision().name(), fee.getPaymentStatus().name(), appointment.getPaymentMethodBrand(),
                appointment.getPaymentMethodLast4(), fee.getFailureMessage(),
                appointment.getAppointmentDateTime().plusMinutes(GRACE_MINUTES), normalDeadline, hardDeadline,
                now.isAfter(normalDeadline) && !now.isAfter(hardDeadline), !now.isAfter(hardDeadline));
    }

    public NoShowFeeDTO preview(Appointment appointment) {
        return noShowFeeRepository.findByAppointmentId(appointment.getId()).map(this::toDto).orElseGet(() -> {
            long servicePrice = MoneySupport.positiveCents(appointment.getPrice()).orElse(0);
            long totalFee = BigDecimal.valueOf(servicePrice)
                    .multiply(BigDecimal.valueOf(FEE_RATE_PERCENT))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
            long depositCredit = Math.min(totalFee, Math.max(0,
                    appointment.getAmountCaptured() == null ? 0 : appointment.getAmountCaptured()));
            LocalDateTime now = salonNow();
            LocalDateTime eligible = appointment.getAppointmentDateTime().plusMinutes(GRACE_MINUTES);
            LocalDateTime normalDeadline = appointment.getAppointmentDateTime().plusHours(NORMAL_WINDOW_HOURS);
            LocalDateTime hardDeadline = appointment.getAppointmentDateTime().plusDays(AUTOMATIC_WINDOW_DAYS);
            boolean statusAllows = appointment.getStatus() == Appointment.AppointmentStatus.APPROVED;
            boolean savedCard = appointment.getCustomer().getStripeCustomerId() != null
                    && appointment.getCustomer().getStripePaymentMethodId() != null
                    && appointment.getOffSessionConsentAt() != null;
            return new NoShowFeeDTO(appointment.getId(), servicePrice, FEE_RATE_PERCENT, totalFee,
                    depositCredit, Math.max(0, totalFee - depositCredit), "AVAILABLE", "NOT_CHARGED",
                    appointment.getPaymentMethodBrand(), appointment.getPaymentMethodLast4(),
                    savedCard ? null : "No reusable saved card with off-session consent", eligible,
                    normalDeadline, hardDeadline, now.isAfter(normalDeadline) && !now.isAfter(hardDeadline),
                    statusAllows && savedCard && !now.isBefore(eligible) && !now.isAfter(hardDeadline));
        });
    }

    private LocalDateTime salonNow() { return LocalDateTime.now(SALON_ZONE); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
