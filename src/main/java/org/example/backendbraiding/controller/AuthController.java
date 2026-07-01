package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.*;
import org.example.backendbraiding.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Login endpoint - validates credentials and returns JWT token
        System.out.println("========== LOGIN ENDPOINT HIT ==========");
        System.out.println("Email: " + request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupAdmin(@RequestBody AdminSetupRequest request) {
        return ResponseEntity.ok(authService.setupAdmin(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(request));
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
