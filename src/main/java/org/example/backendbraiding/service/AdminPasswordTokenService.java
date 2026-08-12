package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.AdminPasswordToken;
import org.example.backendbraiding.repository.AdminPasswordTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AdminPasswordTokenService {
    public static final String INVITATION = "INVITATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    private final AdminPasswordTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminPasswordTokenService(AdminPasswordTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String create(Admin admin, String purpose, Duration lifetime) {
        repository.deleteByAdminAndPurposeAndUsedAtIsNull(admin, purpose);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AdminPasswordToken entity = new AdminPasswordToken();
        entity.setAdmin(admin);
        entity.setPurpose(purpose);
        entity.setTokenHash(hash(raw));
        entity.setExpiresAt(LocalDateTime.now().plus(lifetime));
        repository.save(entity);
        return raw;
    }

    @Transactional(readOnly = true)
    public AdminPasswordToken requireValid(String raw, String purpose) {
        AdminPasswordToken token = repository.findByTokenHashAndPurpose(hash(raw), purpose)
                .orElseThrow(() -> new IllegalArgumentException("This link is invalid or has expired"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("This link is invalid or has expired");
        }
        return token;
    }

    @Transactional
    public void consume(AdminPasswordToken token) {
        token.setUsedAt(LocalDateTime.now());
        repository.save(token);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
