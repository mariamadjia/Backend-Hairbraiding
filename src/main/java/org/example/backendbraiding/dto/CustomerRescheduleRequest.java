package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerRescheduleRequest {
    @NotNull(message = "A new appointment time is required")
    @Future(message = "The new appointment time must be in the future")
    private LocalDateTime appointmentDateTime;
}
