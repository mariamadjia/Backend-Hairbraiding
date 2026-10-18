package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.CheckoutDTO;
import org.example.backendbraiding.dto.CheckoutRefundRequest;
import org.example.backendbraiding.dto.ExternalCheckoutPaymentRequest;
import org.example.backendbraiding.dto.ReceiptRequest;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.service.CheckoutService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CheckoutController {
    private final CheckoutService checkoutService;
    private final AdminRepository adminRepository;

    @GetMapping
    public ResponseEntity<Page<CheckoutDTO>> list(
            @RequestParam(defaultValue = "READY") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean past = "PAST".equalsIgnoreCase(tab);
        Sort sort = Sort.by("appointmentDateTime");
        sort = past ? sort.descending() : sort.ascending();
        return ResponseEntity.ok(checkoutService.list(tab,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort)));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<CheckoutDTO> get(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(checkoutService.get(appointmentId));
    }

    @PostMapping("/{appointmentId}/external-payment")
    public ResponseEntity<CheckoutDTO> externalPayment(@PathVariable Long appointmentId,
            @Valid @RequestBody ExternalCheckoutPaymentRequest request, Authentication authentication) {
        return ResponseEntity.ok(checkoutService.recordExternalPayment(appointmentId, adminId(authentication), request));
    }

    @PostMapping("/{appointmentId}/refunds")
    public ResponseEntity<CheckoutDTO> refund(@PathVariable Long appointmentId,
            @Valid @RequestBody CheckoutRefundRequest request, Authentication authentication) {
        return ResponseEntity.ok(checkoutService.recordExternalRefund(appointmentId, adminId(authentication), request));
    }

    @PostMapping("/{appointmentId}/receipt")
    public ResponseEntity<CheckoutDTO> receipt(@PathVariable Long appointmentId,
            @RequestBody(required = false) ReceiptRequest request) {
        return ResponseEntity.ok(checkoutService.sendReceipt(appointmentId, request));
    }

    private Long adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator authentication is required");
        }
        Admin admin = adminRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(candidate -> "ACTIVE".equalsIgnoreCase(candidate.getStatus()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Administrator account unavailable"));
        return admin.getId();
    }
}
