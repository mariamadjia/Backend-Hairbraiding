package org.example.backendbraiding.controller;

import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonParser;
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
import org.example.backendbraiding.service.StripeWebhookEventService;
import org.example.backendbraiding.service.NoShowService;
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
    private final StripeWebhookEventService webhookEventService;
    private final NoShowService noShowService;

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
        if (!webhookEventService.begin(event.getId(), event.getType())) {
            return ResponseEntity.ok("Webhook already received");
        }
        try {
            switch (event.getType()) {
                case "payment_intent.succeeded",
                     "payment_intent.payment_failed",
                     "payment_intent.canceled" -> {
                        String paymentIntentId = requirePaymentIntentId(event);
                        if (!noShowService.synchronize(paymentIntentId)) {
                            paymentService.synchronizePaymentIntent(paymentIntentId);
                        }
                    }
                case "payment_intent.amount_capturable_updated" ->
                        handlePaymentIntentAmountCapturableUpdated(requirePaymentIntentId(event));
                default -> log.info("Unhandled event type: {}", event.getType());
            }
            webhookEventService.processed(event.getId());
            return ResponseEntity.ok("Webhook received");
        } catch (RuntimeException exception) {
            // A non-2xx response asks Stripe to retry instead of silently losing
            // an event that could not be deserialized or persisted.
            log.error("Could not process Stripe event {}", event.getId(), exception);
            webhookEventService.failed(event.getId(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    private String requirePaymentIntentId(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast)
                .map(PaymentIntent::getId)
                .orElseGet(() -> paymentIntentIdFromRawJson(
                        event.getDataObjectDeserializer().getRawJson()));
    }

    static String paymentIntentIdFromRawJson(String rawJson) {
        try {
            String id = JsonParser.parseString(rawJson).getAsJsonObject().get("id").getAsString();
            if (id == null || !id.startsWith("pi_")) throw new IllegalStateException("Invalid PaymentIntent ID");
            return id;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stripe event does not contain a valid PaymentIntent ID", exception);
        }
    }

    private void handlePaymentIntentAmountCapturableUpdated(String paymentIntentId) {
        log.info("Payment authorized for PaymentIntent: {}", paymentIntentId);
        paymentService.synchronizePaymentIntent(paymentIntentId);

        Optional<Appointment> appointmentOpt = appointmentRepository
                .findByPaymentIntentId(paymentIntentId);

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
                        paymentService.capturePayment(new PaymentCaptureRequest(paymentIntentId, null));
                    } catch (Exception e) {
                        paymentService.markCaptureFailed(paymentIntentId, e.getMessage());
                        log.error("Automatic capture failed for appointment {}", appointment.getId(), e);
                    }
                }
            }
        }
    }
}
