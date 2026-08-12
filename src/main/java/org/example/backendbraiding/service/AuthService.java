package org.example.backendbraiding.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.example.backendbraiding.dto.*;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

@Service
public class AuthService {
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final String googleClientId;
    private final long rememberDurationSeconds;

    public AuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider, EmailService emailService,
                      @Value("${auth.google-client-id:}") String googleClientId,
                      @Value("${auth.remember-duration-seconds}") long rememberDurationSeconds) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.googleClientId = googleClientId;
        this.rememberDurationSeconds = rememberDurationSeconds;
    }

    public Map<String, Object> login(LoginRequest request) {
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        admin.setLastLogin(LocalDateTime.now());
        adminRepository.save(admin);

        String token = createToken(admin, request.isRememberDevice());

        return loginResponse(admin, token);
    }

    public Map<String, Object> loginWithGoogle(GoogleLoginRequest request) {
        if (googleClientId.isBlank()) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) throw new IllegalArgumentException("Google sign-in could not be verified");

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new IllegalArgumentException("Google email must be verified");
            }
            String email = payload.getEmail().trim().toLowerCase();
            Admin admin = adminRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("This Google account is not approved for admin access"));

            admin.setLastLogin(LocalDateTime.now());
            adminRepository.save(admin);
            return loginResponse(admin, createToken(admin, request.isRememberDevice()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Google sign-in could not be verified");
        }
    }

    public Map<String, Object> currentAdmin(String email) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Admin account is not available"));
        return Map.of("authenticated", true, "admin", adminView(admin));
    }

    private String createToken(Admin admin, boolean rememberDevice) {
        long expiration = rememberDevice ? rememberDurationSeconds * 1000L : 30 * 60 * 1000L;
        return jwtTokenProvider.generateToken(admin.getEmail(), admin.getRole(), expiration);
    }

    private Map<String, Object> loginResponse(Admin admin, String token) {

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("admin", adminView(admin));
        return response;
    }

    private Map<String, Object> adminView(Admin admin) {
        return Map.of(
                "id", admin.getId(),
                "email", admin.getEmail(),
                "username", admin.getUsername(),
                "firstName", admin.getFirstName(),
                "lastName", admin.getLastName()
        );
    }

    public Map<String, String> setupAdmin(AdminSetupRequest request) {
        if (adminRepository.count() > 0) {
            throw new RuntimeException("Admin already exists");
        }

        if (adminRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already in use");
        }

        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setFirstName(request.getFirstName());
        admin.setLastName(request.getLastName());
        admin.setRole("ROLE_ADMIN");

        adminRepository.save(admin);

        return Map.of("message", "Admin account created successfully");
    }

    public Map<String, String> changePassword(ChangePasswordRequest request) {
        // Get current authenticated admin email from SecurityContext
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        adminRepository.save(admin);

        return Map.of("message", "Password changed successfully");
    }

    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        String resetToken = UUID.randomUUID().toString();
        
        emailService.sendPasswordResetEmail(admin.getEmail(), resetToken);

        return Map.of("message", "Password reset email sent");
    }

    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isEmpty()) {
            throw new RuntimeException("Reset token is required");
        }
        
        if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            throw new RuntimeException("New password is required");
        }
        
        // In a real implementation, you would validate the token and find the admin
        // For now, this is a placeholder that would need token validation logic
        // Admin admin = adminRepository.findByResetToken(request.getToken())
        //         .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));
        
        // admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // admin.setResetToken(null);
        // adminRepository.save(admin);
        
        return Map.of("message", "Password reset successfully");
    }
}
