package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ManagedAppointmentDTO {
    private Long id;
    private String customerFirstName;
    private String maskedEmail;
    private String serviceName;
    private String selectedSize;
    private String selectedLength;
    private String selectedFoundation;
    private LocalDateTime appointmentDateTime;
    private LocalDateTime appointmentEndDateTime;
    private String status;
    private Long depositPaidCents;
    private LocalDateTime changeDeadlineAt;
    private int selfServiceChangesRemaining;
    private boolean canCancel;
    private boolean canReschedule;
    private String lockReason;
}
