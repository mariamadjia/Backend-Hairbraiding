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

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Webhook secret not configured. Skipping signature verification.");
            return processWebhookEvent(payload);
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

    private ResponseEntity<String> processWebhookEvent(String payload) {
        try {
            Event event = Event.GSON.fromJson(payload, Event.class);
            return handleEvent(event);
        } catch (Exception e) {
            log.error("Error processing webhook event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error processing event");
        }
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
                appointmentRepository.save(appointment);
                log.info("Updated appointment {} payment status to AUTHORIZED", appointment.getId());
            }
        }
    }
}
