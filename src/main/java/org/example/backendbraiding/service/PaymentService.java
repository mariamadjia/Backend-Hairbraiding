package org.example.backendbraiding.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
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

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        try {
            Map<String, String> metadata = new HashMap<>();
            if (request.getAppointmentId() != null) {
                metadata.put("appointmentId", request.getAppointmentId().toString());
            }
            if (request.getCustomerEmail() != null) {
                metadata.put("customerEmail", request.getCustomerEmail());
            }
            if (request.getCustomerName() != null) {
                metadata.put("customerName", request.getCustomerName());
            }

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(request.getAmount())
                    .setCurrency(request.getCurrency())
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putAllMetadata(metadata)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    );

            // Only set payment method and confirm if provided
            if (request.getPaymentMethodId() != null) {
                paramsBuilder.setPaymentMethod(request.getPaymentMethodId());
                paramsBuilder.setConfirm(true);
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            if (request.getAppointmentId() != null) {
                updateAppointmentWithPayment(
                        request.getAppointmentId(),
                        paymentIntent.getId(),
                        request.getAmount(),
                        paymentIntent.getPaymentMethod()
                );
            }

            return PaymentIntentResponse.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .clientSecret(paymentIntent.getClientSecret())
                    .status(paymentIntent.getStatus())
                    .amount(paymentIntent.getAmount())
                    .currency(paymentIntent.getCurrency())
                    .message("Payment intent created successfully.")
                    .appointmentId(request.getAppointmentId())
                    .build();

        } catch (StripeException e) {
            log.error("Error creating payment intent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment intent: " + e.getMessage());
        }
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

    private void updateAppointmentWithPayment(Long appointmentId, String paymentIntentId, 
                                             Long amount, String paymentMethodId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        appointment.setPaymentIntentId(paymentIntentId);
        appointment.setDepositAmount(amount);
        appointment.setPaymentStatus(Appointment.PaymentStatus.AUTHORIZED);

        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            if (paymentMethod.getCard() != null) {
                appointment.setPaymentMethodLast4(paymentMethod.getCard().getLast4());
                appointment.setPaymentMethodBrand(paymentMethod.getCard().getBrand());
            }
        } catch (StripeException e) {
            log.warn("Could not retrieve payment method details: {}", e.getMessage());
        }

        appointmentRepository.save(appointment);
    }
}
