package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.PaymentCaptureRequest;
import org.example.backendbraiding.dto.PaymentIntentRequest;
import org.example.backendbraiding.dto.PaymentIntentResponse;
import org.example.backendbraiding.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request) {
        PaymentIntentResponse response = paymentService.createPaymentIntent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/capture")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentIntentResponse> capturePayment(
            @Valid @RequestBody PaymentCaptureRequest request) {
        PaymentIntentResponse response = paymentService.capturePayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel/{paymentIntentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentIntentResponse> cancelPayment(
            @PathVariable String paymentIntentId) {
        PaymentIntentResponse response = paymentService.cancelPayment(paymentIntentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{paymentIntentId}")
    public ResponseEntity<PaymentIntentResponse> getPaymentStatus(
            @PathVariable String paymentIntentId) {
        PaymentIntentResponse response = paymentService.getPaymentStatus(paymentIntentId);
        return ResponseEntity.ok(response);
    }
}
