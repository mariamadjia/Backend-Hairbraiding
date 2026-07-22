package org.example.backendbraiding.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final long DEPOSIT_AMOUNT_CENTS = 5000L;

    private final AppointmentRepository appointmentRepository;
    private final BookingPaymentTokenService bookingPaymentTokenService;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        if (!bookingPaymentTokenService.isValidForAppointment(request.getPaymentToken(), request.getAppointmentId())) {
            throw new IllegalArgumentException("Invalid or expired payment token");
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

        if (appointment.getStatus() != Appointment.AppointmentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be authorized for a pending appointment");
        }

        try {
            if (appointment.getPaymentIntentId() != null &&
                    appointment.getPaymentStatus() == Appointment.PaymentStatus.PENDING) {
                PaymentIntent existingIntent = PaymentIntent.retrieve(appointment.getPaymentIntentId());
                return paymentIntentResponse(existingIntent, appointment.getId(), "Payment intent ready for authorization.");
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("appointmentId", appointment.getId().toString());
            metadata.put("customerEmail", appointment.getCustomer().getEmail());
            metadata.put("customerName", appointment.getCustomer().getFirstName() + " " + appointment.getCustomer().getLastName());

            PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(DEPOSIT_AMOUNT_CENTS)
                    .setCurrency("usd")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putAllMetadata(metadata)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .build());

            appointment.setPaymentIntentId(paymentIntent.getId());
            appointment.setDepositAmount(DEPOSIT_AMOUNT_CENTS);
            appointment.setPaymentStatus(Appointment.PaymentStatus.PENDING);
            appointmentRepository.save(appointment);

            return paymentIntentResponse(paymentIntent, appointment.getId(), "Payment intent created successfully.");
        } catch (StripeException e) {
            log.error("Error creating payment intent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment intent");
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

            appointment.setPaymentStatus(Appointment.PaymentStatus.CAPTURED);
            appointment.setPaymentCapturedAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

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
            throw new RuntimeException("Failed to capture payment: " + e.getMessage());
        }
    }

    @Transactional
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
            throw new RuntimeException("Failed to cancel payment: " + e.getMessage());
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
            throw new RuntimeException("Failed to retrieve payment status: " + e.getMessage());
        }
    }

}
