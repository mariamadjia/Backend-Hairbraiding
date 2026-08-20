package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class NoShowChargeRequest {
    private boolean confirmOverdue;
    private String feeDecision = "ACTIVE";
    @PositiveOrZero(message = "Adjusted fee cannot be negative")
    private Long adjustedTotalFeeCents;
    @Size(max = 500, message = "Admin note cannot exceed 500 characters")
    private String adminNote;
}
