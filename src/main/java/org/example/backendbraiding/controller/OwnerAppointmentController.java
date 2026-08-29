package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.OwnerAppointmentRequest;
import org.example.backendbraiding.dto.OwnerAppointmentResponse;
import org.example.backendbraiding.dto.OwnerDepositPageDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.service.OwnerAppointmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OwnerAppointmentController {
    private final OwnerAppointmentService service;
    private final AdminRepository adminRepository;

    @PostMapping("/api/admin/appointments")
    public ResponseEntity<OwnerAppointmentResponse> create(
            @Valid @RequestBody OwnerAppointmentRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId(authentication)));
    }

    @PostMapping("/api/admin/appointments/{id}/deposit-link/resend")
    public OwnerAppointmentResponse resend(@PathVariable Long id, Authentication authentication) {
        return service.resend(id, adminId(authentication));
    }

    @PostMapping("/api/admin/appointments/{id}/deposit/waive")
    public OwnerAppointmentResponse waive(@PathVariable Long id, Authentication authentication) {
        return service.waive(id, adminId(authentication));
    }

    @GetMapping("/api/public/deposits/{id}")
    public OwnerDepositPageDTO depositPage(@PathVariable Long id, @RequestParam String token) {
        return service.depositPage(id, token);
    }

    private Long adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator authentication is required");
        }
        return adminRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(admin -> "ACTIVE".equalsIgnoreCase(admin.getStatus()))
                .map(Admin::getId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "The authenticated administrator account is unavailable"));
    }
}
