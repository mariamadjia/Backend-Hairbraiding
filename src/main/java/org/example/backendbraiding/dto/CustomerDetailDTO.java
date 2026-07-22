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
    LocalDateTime nextAppointmentDate,
    Integer totalAppointments,
    Integer completedVisits,
    Integer upcomingAppointments,
    BigDecimal totalSpent,
    BigDecimal averageAppointmentValue,
    List<AppointmentSummaryDTO> appointments,
    int appointmentPage,
    int appointmentTotalPages,
    long appointmentTotalElements,
    String notes
) {
    public record AppointmentSummaryDTO(
        Long id,
        String serviceName,
        LocalDateTime appointmentDateTime,
        LocalDateTime appointmentEndDateTime,
        Integer durationMinutes,
        String status,
        String paymentStatus,
        BigDecimal amountPaid
    ) {}
}
