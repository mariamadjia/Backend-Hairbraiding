package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDateTime firstAppointmentDate;
    private LocalDateTime lastAppointmentDate;
    private Integer totalAppointments;
    private BigDecimal totalSpent;
    private BigDecimal averageAppointmentValue;
    private List<AppointmentSummaryDTO> appointments;
    private String notes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentSummaryDTO {
        private Long id;
        private String serviceName;
        private LocalDateTime appointmentDateTime;
        private String status;
        private BigDecimal amountPaid;
    }
}
