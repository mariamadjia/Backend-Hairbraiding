package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerCancelRequest {
    @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
    private String reason;
}
