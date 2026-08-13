package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockedTimeSlotDTO {
    private Long id;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason;
    private Boolean isRecurring;
    private String recurrencePattern;
    private LocalDate recurrenceEndDate;
    private String createdByName;
    private LocalDateTime createdAt;
}
