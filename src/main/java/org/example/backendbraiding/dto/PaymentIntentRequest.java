package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentRequest {
    
    @NotNull(message = "Amount is required")
    private Long amount;
    
    @NotBlank(message = "Currency is required")
    private String currency = "usd";
    
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
    
    private Long appointmentId;
    
    private String customerEmail;
    
    private String customerName;
}
