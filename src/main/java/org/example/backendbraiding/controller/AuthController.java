package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.*;
import org.example.backendbraiding.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.backendbraiding.security.AuthCookieService;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final String setupSecret;

    public AuthController(AuthService authService, AuthCookieService authCookieService,
                          @Value("${auth.setup-secret:}") String setupSecret) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.setupSecret = setupSecret;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Login attempt for: {}", request.getEmail());
        Map<String, Object> result = authService.login(request);
        authCookieService.issue(response, (String) result.remove("token"), request.isRememberDevice());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        Map<String, Object> result = authService.loginWithGoogle(request);
        authCookieService.issue(response, (String) result.remove("token"), request.isRememberDevice());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/session")
    public ResponseEntity<?> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(authService.currentAdmin(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        authCookieService.clear(response);
        return ResponseEntity.ok(Map.of("message", "Signed out"));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupAdmin(@RequestHeader(value = "X-Setup-Secret", required = false) String providedSecret,
                                        @RequestBody AdminSetupRequest request) {
        if (setupSecret.isBlank() || !setupSecret.equals(providedSecret)) {
            return ResponseEntity.status(404).build();
        }
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
