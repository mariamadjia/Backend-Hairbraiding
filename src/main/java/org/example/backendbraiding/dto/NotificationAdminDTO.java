package org.example.backendbraiding.dto;

import java.time.LocalDateTime;

public record NotificationAdminDTO(
        Long id,
        Long appointmentId,
        String customerName,
        String channel,
        String recipient,
        String subject,
        String status,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
