package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSettingsDTO {
    @NotNull(message = "Slot duration is required")
    @Min(value = 1, message = "Slot duration must be at least 1 minute")
    private Integer slotDurationMinutes;
    
    @NotNull(message = "Advance booking days is required")
    @Min(value = 0, message = "Advance booking days cannot be negative")
    private Integer advanceBookingDays;
    
    @NotNull(message = "Max appointments per slot is required")
    @Min(value = 1, message = "Max appointments per slot must be at least 1")
    private Integer maxAppointmentsPerSlot;
    
    @NotNull(message = "Require approval setting is required")
    private Boolean requireApproval;
    
    @NotNull(message = "Allow same day booking setting is required")
    private Boolean allowSameDayBooking;

    @NotNull(message = "Buffer time is required")
    @Min(value = 0, message = "Buffer time cannot be negative")
    private Integer bufferTimeBetweenAppointments;

    @NotNull(message = "Timezone is required")
    @Size(max = 50, message = "Timezone cannot exceed 50 characters")
    private String timezone;
    
    private LocalDateTime updatedAt;
    private String updatedByName;
}
