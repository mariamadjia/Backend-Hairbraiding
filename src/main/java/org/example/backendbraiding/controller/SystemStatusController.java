package org.example.backendbraiding.controller;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.model.NotificationOutbox;
import org.example.backendbraiding.repository.NotificationOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-status")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemStatusController {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxRepository outboxRepository;

    @Value("${stripe.api.key:}") private String stripeKey;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${spring.mail.password:}") private String mailPassword;
    @Value("${vonage.api.key:}") private String vonageKey;
    @Value("${vonage.api.secret:}") private String vonageSecret;
    @Value("${vonage.phone.number:}") private String vonageNumber;

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", Instant.now().toString());
        result.put("application", "AVAILABLE");
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("database", "AVAILABLE");
        } catch (RuntimeException exception) {
            result.put("database", "UNAVAILABLE");
        }
        result.put("stripe", configured(stripeKey, "sk_"));
        result.put("email", configured(mailUsername, "your-") && configured(mailPassword, "your-"));
        result.put("sms", configured(vonageKey, "your_") && configured(vonageSecret, "your_") && configured(vonageNumber, "your_"));
        result.put("pendingNotifications", outboxRepository.countByStatus(NotificationOutbox.Status.PENDING));
        result.put("failedNotifications", outboxRepository.countByStatus(NotificationOutbox.Status.FAILED));
        return result;
    }

    private boolean configured(String value, String placeholderPrefix) {
        return value != null && !value.isBlank() && !value.startsWith(placeholderPrefix);
    }
}
