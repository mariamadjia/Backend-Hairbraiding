package org.example.backendbraiding.controller;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.model.Appointment;
import org.example.backendbraiding.repository.AppointmentRepository;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.example.backendbraiding.service.PaymentService;
import org.example.backendbraiding.dto.PaymentCaptureRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSettingsRepository settingsRepository;
    private final PaymentService paymentService;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/stripe")
    @org.springframework.cache.annotation.CacheEvict(value = {"appointments", "availableSlots"}, allEntries = true)
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        if (webhookSecret == null || !webhookSecret.startsWith("whsec_")) {
            log.error("Stripe webhook secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (JsonSyntaxException e) {
            log.error("Invalid payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        return handleEvent(event);
    }


    private ResponseEntity<String> handleEvent(Event event) {
        log.info("Received Stripe webhook event: {}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded":
                handlePaymentIntentSucceeded(event);
                break;
            case "payment_intent.payment_failed":
                handlePaymentIntentFailed(event);
                break;
            case "payment_intent.canceled":
                handlePaymentIntentCanceled(event);
                break;
            case "payment_intent.amount_capturable_updated":
                handlePaymentIntentAmountCapturableUpdated(event);
                break;
            default:
                log.info("Unhandled event type: {}", event.getType());
        }

        return ResponseEntity.ok("Webhook received");
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in payment_intent.succeeded event");
            return;
        }

        log.info("Payment succeeded for PaymentIntent: {}", paymentIntent.getId());

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntent.getId());

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            
            if ("succeeded".equals(paymentIntent.getStatus())) {
                appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
                appointment.setPaymentCapturedAt(LocalDateTime.now());
                appointmentRepository.save(appointment);
                log.info("Updated appointment {} payment status to CAPTURED", appointment.getId());
            }
        } else {
            log.warn("No appointment found for PaymentIntent: {}", paymentIntent.getId());
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in payment_intent.payment_failed event");
            return;
        }

        log.error("Payment failed for PaymentIntent: {}", paymentIntent.getId());

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntent.getId());

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            appointment.setPaymentStatus(Appointment.PaymentStatus.FAILED);
            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appointmentRepository.save(appointment);
            log.info("Updated appointment {} payment status to FAILED", appointment.getId());
        }
    }

    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in payment_intent.canceled event");
            return;
        }

        log.info("Payment canceled for PaymentIntent: {}", paymentIntent.getId());

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntent.getId());

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            appointment.setPaymentStatus(Appointment.PaymentStatus.CANCELLED);
            if (appointment.getStatus() == Appointment.AppointmentStatus.PENDING) {
                appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            }
            appointmentRepository.save(appointment);
            log.info("Updated appointment {} payment status to CANCELLED", appointment.getId());
        }
    }

    private void handlePaymentIntentAmountCapturableUpdated(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in amount_capturable_updated event");
            return;
        }

        log.info("Payment authorized for PaymentIntent: {}", paymentIntent.getId());

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntent.getId());

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.PENDING) {
                appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);
                appointment.setPaymentPendingExpiresAt(null);
                appointmentRepository.save(appointment);
                log.info("Updated appointment {} payment status to AUTHORIZED", appointment.getId());

                boolean requireApproval = settingsRepository.findFirstByOrderByIdDesc()
                        .map(settings -> settings.getRequireApproval())
                        .orElse(true);
                if (!requireApproval) {
                    try {
                        paymentService.capturePayment(new PaymentCaptureRequest(paymentIntent.getId(), null));
                        Appointment capturedAppointment = appointmentRepository.findById(appointment.getId())
                                .orElseThrow(() -> new IllegalStateException("Appointment disappeared during capture"));
                        capturedAppointment.setStatus(Appointment.AppointmentStatus.APPROVED);
                        capturedAppointment.setApprovedAt(LocalDateTime.now());
                        appointmentRepository.save(capturedAppointment);
                    } catch (Exception e) {
                        paymentService.markCaptureFailed(paymentIntent.getId(), e.getMessage());
                        log.error("Automatic capture failed for appointment {}", appointment.getId(), e);
                    }
                }
            }
        }
    }
}
