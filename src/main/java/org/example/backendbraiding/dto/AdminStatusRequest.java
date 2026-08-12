package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminStatusRequest {
    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;
}
