package org.example.backendbraiding.dto;

import java.time.LocalDateTime;

public record AppointmentEventDTO(
        Long id, String eventType, String appointmentStatus, String paymentStatus,
        String actorName, String reason, LocalDateTime createdAt) {}
