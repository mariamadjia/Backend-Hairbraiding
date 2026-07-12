package org.example.backendbraiding.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerDetailDTO(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    LocalDateTime firstAppointmentDate,
    LocalDateTime lastAppointmentDate,
    Integer totalAppointments,
    BigDecimal totalSpent,
    BigDecimal averageAppointmentValue,
    List<AppointmentSummaryDTO> appointments,
    String notes
) {
    public record AppointmentSummaryDTO(
        Long id,
        String serviceName,
        LocalDateTime appointmentDateTime,
        String status,
        BigDecimal amountPaid
    ) {}
}
