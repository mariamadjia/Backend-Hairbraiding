package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.PaymentCaptureRequest;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Orchestrates separate committed transactions; provider failures must not erase approval intent. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryService {
    private final PaymentService payments;
    private final AppointmentRepository appointments;

    public void process(String paymentIntentId) {
        payments.synchronizePaymentIntent(paymentIntentId);
        Appointment appointment = appointments.findByPaymentIntentId(paymentIntentId).orElseThrow();
        if (appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURED
                || appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLED) return;
        boolean terminal = appointment.getStatus() == Appointment.AppointmentStatus.DENIED
                || appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED;
        boolean expired = appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                && (!appointment.getAppointmentDateTime().isAfter(LocalDateTime.now(ZoneId.of("America/Chicago")))
                || (appointment.getPaymentAuthorizationExpiresAt() != null
                && PaymentLifecycleRules.isAuthorizationExpired(appointment.getPaymentAuthorizationExpiresAt(), LocalDateTime.now()))
                || (appointment.getPaymentPendingExpiresAt() != null
                && !appointment.getPaymentPendingExpiresAt().isAfter(LocalDateTime.now())));
        if (terminal || expired || appointment.getPaymentStatus() == Appointment.PaymentStatus.CANCELLATION_FAILED) {
            try {
                payments.cancelPayment(paymentIntentId);
            } catch (RuntimeException failure) {
                payments.markCancellationFailed(paymentIntentId, failure.getMessage());
                throw failure;
            }
        } else if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING
                && appointment.getApprovedAt() != null
                && (appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED
                || appointment.getPaymentStatus() == Appointment.PaymentStatus.CAPTURE_FAILED)) {
            try {
                payments.capturePayment(new PaymentCaptureRequest(paymentIntentId, null));
            } catch (RuntimeException failure) {
                payments.markCaptureFailed(paymentIntentId, failure.getMessage());
                throw failure;
            }
        }
    }

    @Scheduled(fixedDelayString = "${stripe.authorization-expiry.interval-ms:60000}")
    public void releaseExpiredAuthorizations() {
        for (Appointment appointment : appointments.findExpiredAuthorizations(LocalDateTime.now())) {
            try {
                process(appointment.getPaymentIntentId());
            } catch (RuntimeException failure) {
                log.warn("Authorization release will retry appointment {}: {}", appointment.getId(), failure.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${stripe.reconciliation.interval-ms:300000}")
    public void reconcilePaymentStates() {
        for (Appointment appointment : appointments.findAppointmentsNeedingPaymentReconciliation()) {
            try {
                process(appointment.getPaymentIntentId());
            } catch (RuntimeException failure) {
                log.warn("Payment recovery will retry appointment {}: {}", appointment.getId(), failure.getMessage());
            }
        }
    }
}
