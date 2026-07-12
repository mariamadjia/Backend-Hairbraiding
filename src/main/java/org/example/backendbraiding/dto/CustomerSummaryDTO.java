package org.example.backendbraiding.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerSummaryDTO(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    LocalDateTime lastAppointmentDate,
    Integer totalAppointments,
    BigDecimal totalSpent
) {}
