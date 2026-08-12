package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.AdminInviteRequest;
import org.example.backendbraiding.dto.PasswordTokenRequest;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.AdminPasswordToken;
import org.example.backendbraiding.repository.AdminRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AdministratorService {
    private static final Logger log = LoggerFactory.getLogger(AdministratorService.class);
    private final AdminRepository admins;
    private final AdminPasswordTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AdministratorService(AdminRepository admins, AdminPasswordTokenService tokens,
                                PasswordEncoder passwordEncoder, EmailService emailService) {
        this.admins = admins;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return admins.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> invite(AdminInviteRequest request) {
        String email = normalize(request.getEmail());
        if (admins.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("An administrator with this email already exists");
        Admin inviter = currentAdmin();
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setUsername(uniqueUsername(email));
        admin.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        admin.setFirstName(request.getFirstName().trim());
        admin.setLastName(request.getLastName().trim());
        admin.setRole("ROLE_ADMIN");
        admin.setStatus("PENDING");
        admin.setPasswordConfigured(false);
        admin.setInvitedAt(LocalDateTime.now());
        admin.setInvitedBy(inviter);
        admin = admins.save(admin);
        sendInvitation(admin);
        return view(admin);
    }

    @Transactional
    public void resendInvitation(Long id) {
        Admin admin = require(id);
        if (!"PENDING".equals(admin.getStatus())) throw new IllegalArgumentException("Only pending invitations can be resent");
        admin.setInvitedAt(LocalDateTime.now());
        admins.save(admin);
        sendInvitation(admin);
    }

    @Transactional
    public void sendReset(Long id) {
        Admin admin = require(id);
        if (!"ACTIVE".equals(admin.getStatus())) throw new IllegalArgumentException("Only active administrators can reset a password");
        String raw = tokens.create(admin, AdminPasswordTokenService.PASSWORD_RESET, Duration.ofMinutes(30));
        emailService.sendPasswordResetEmail(admin.getEmail(), raw);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, String status) {
        Admin current = currentAdmin();
        Admin target = require(id);
        if (current.getId().equals(target.getId()) && "DISABLED".equals(status)) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        if ("DISABLED".equals(status) && "ACTIVE".equals(target.getStatus()) && admins.countByStatus("ACTIVE") <= 1) {
            throw new IllegalArgumentException("At least one active administrator is required");
        }
        target.setStatus(status);
        target.setSessionVersion(target.getSessionVersion() + 1);
        admins.save(target);
        return view(target);
    }

    @Transactional
    public void remove(Long id) {
        Admin current = currentAdmin();
        Admin target = require(id);
        if (current.getId().equals(target.getId())) throw new IllegalArgumentException("You cannot remove your own account");
        if ("ACTIVE".equals(target.getStatus()) && admins.countByStatus("ACTIVE") <= 1) {
            throw new IllegalArgumentException("At least one active administrator is required");
        }
        admins.delete(target);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateToken(String raw, String purpose) {
        AdminPasswordToken token = tokens.requireValid(raw, purpose);
        return Map.of("valid", true, "email", token.getAdmin().getEmail(), "purpose", purpose);
    }

    @Transactional
    public void setPassword(PasswordTokenRequest request, String purpose) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) throw new IllegalArgumentException("Passwords do not match");
        if (request.getNewPassword().length() < 12 || request.getNewPassword().length() > 128) throw new IllegalArgumentException("Password must be between 12 and 128 characters");
        AdminPasswordToken token = tokens.requireValid(request.getToken(), purpose);
        Admin admin = token.getAdmin();
        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        admin.setPasswordConfigured(true);
        admin.setStatus("ACTIVE");
        admin.setActivatedAt(LocalDateTime.now());
        admin.setSessionVersion(admin.getSessionVersion() + 1);
        admins.save(admin);
        tokens.consume(token);
        try {
            emailService.sendPasswordChangedEmail(admin.getEmail());
        } catch (RuntimeException exception) {
            // The password change is authoritative; a temporary mail outage must not undo it.
            log.error("Password changed but confirmation email could not be delivered for admin {}", admin.getId(), exception);
        }
    }

    @Transactional
    public void requestReset(String email) {
        try {
            admins.findByEmailIgnoreCase(normalize(email)).filter(a -> "ACTIVE".equals(a.getStatus())).ifPresent(admin -> {
                String raw = tokens.create(admin, AdminPasswordTokenService.PASSWORD_RESET, Duration.ofMinutes(30));
                emailService.sendPasswordResetEmail(admin.getEmail(), raw);
            });
        } catch (RuntimeException exception) {
            log.error("Password-reset delivery failed", exception);
        }
    }

    private void sendInvitation(Admin admin) {
        String raw = tokens.create(admin, AdminPasswordTokenService.INVITATION, Duration.ofHours(24));
        emailService.sendAdminInvitation(admin.getEmail(), admin.getFirstName(), raw);
    }

    private Admin currentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return admins.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("Administrator is unavailable"));
    }

    private Admin require(Long id) { return admins.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrator was not found")); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String uniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9]", "");
        if (base.isBlank()) base = "admin";
        String candidate = base;
        int suffix = 2;
        while (admins.existsByUsername(candidate)) candidate = base + suffix++;
        return candidate;
    }

    private Map<String, Object> view(Admin admin) {
        return Map.ofEntries(
                Map.entry("id", admin.getId()), Map.entry("firstName", admin.getFirstName()),
                Map.entry("lastName", admin.getLastName()), Map.entry("email", admin.getEmail()),
                Map.entry("username", admin.getUsername()), Map.entry("role", admin.getRole()),
                Map.entry("status", admin.getStatus()), Map.entry("passwordConfigured", admin.getPasswordConfigured()),
                Map.entry("lastLogin", admin.getLastLogin() == null ? "" : admin.getLastLogin().toString()),
                Map.entry("invitedAt", admin.getInvitedAt() == null ? "" : admin.getInvitedAt().toString()));
    }
}
