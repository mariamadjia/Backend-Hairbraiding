package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnerRescheduleRequest {
    @NotNull(message = "A new appointment time is required")
    private LocalDateTime appointmentDateTime;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
