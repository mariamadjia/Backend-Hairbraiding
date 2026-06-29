package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCaptureRequest {
    
    @NotBlank(message = "Payment intent ID is required")
    private String paymentIntentId;
    
    private Long amountToCapture;
}
