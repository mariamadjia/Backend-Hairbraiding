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
        try {
            switch (event.getType()) {
                case "payment_intent.succeeded",
                     "payment_intent.payment_failed",
                     "payment_intent.canceled" ->
                        paymentService.synchronizePaymentIntent(requirePaymentIntent(event).getId());
                case "payment_intent.amount_capturable_updated" ->
                        handlePaymentIntentAmountCapturableUpdated(requirePaymentIntent(event));
                default -> log.info("Unhandled event type: {}", event.getType());
            }
            return ResponseEntity.ok("Webhook received");
        } catch (RuntimeException exception) {
            // A non-2xx response asks Stripe to retry instead of silently losing
            // an event that could not be deserialized or persisted.
            log.error("Could not process Stripe event {}", event.getId(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    private PaymentIntent requirePaymentIntent(Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Stripe event object could not be deserialized; check webhook API version"));
    }

    private void handlePaymentIntentAmountCapturableUpdated(PaymentIntent paymentIntent) {
        log.info("Payment authorized for PaymentIntent: {}", paymentIntent.getId());
        paymentService.synchronizePaymentIntent(paymentIntent.getId());

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntent.getId());

        if (appointmentOpt.isPresent()) {
            Appointment appointment = appointmentOpt.get();
            
            if (appointment.getPaymentStatus() == Appointment.PaymentStatus.AUTHORIZED) {
                boolean requireApproval = settingsRepository.findFirstByOrderByIdDesc()
                        .map(settings -> settings.getRequireApproval())
                        .orElse(true);
                if (!requireApproval) {
                    try {
                        appointment.setStatus(Appointment.AppointmentStatus.PENDING);
                        appointment.setApprovedAt(LocalDateTime.now());
                        appointmentRepository.save(appointment);
                        paymentService.capturePayment(new PaymentCaptureRequest(paymentIntent.getId(), null));
                    } catch (Exception e) {
                        paymentService.markCaptureFailed(paymentIntent.getId(), e.getMessage());
                        log.error("Automatic capture failed for appointment {}", appointment.getId(), e);
                    }
                }
            }
        }
    }
}
