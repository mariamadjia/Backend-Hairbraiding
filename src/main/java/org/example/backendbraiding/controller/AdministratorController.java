package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import org.example.backendbraiding.dto.AdminInviteRequest;
import org.example.backendbraiding.dto.AdminStatusRequest;
import org.example.backendbraiding.service.AdministratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/administrators")
public class AdministratorController {
    private final AdministratorService service;
    public AdministratorController(AdministratorService service) { this.service = service; }

    @GetMapping public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }
    @PostMapping("/invite") public ResponseEntity<?> invite(@Valid @RequestBody AdminInviteRequest request) { return ResponseEntity.ok(service.invite(request)); }
    @PostMapping("/{id}/resend-invitation") public ResponseEntity<?> resend(@PathVariable Long id) { service.resendInvitation(id); return ResponseEntity.ok(Map.of("message", "Invitation sent")); }
    @PostMapping("/{id}/send-password-reset") public ResponseEntity<?> reset(@PathVariable Long id) { service.sendReset(id); return ResponseEntity.ok(Map.of("message", "Password reset sent")); }
    @PatchMapping("/{id}/status") public ResponseEntity<?> status(@PathVariable Long id, @Valid @RequestBody AdminStatusRequest request) { return ResponseEntity.ok(service.updateStatus(id, request.getStatus())); }
    @DeleteMapping("/{id}") public ResponseEntity<?> remove(@PathVariable Long id) { service.remove(id); return ResponseEntity.noContent().build(); }
}
