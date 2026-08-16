package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoShowChargeRequest {
    private boolean confirmOverdue;
    @Size(max = 500, message = "Admin note cannot exceed 500 characters")
    private String adminNote;
}
