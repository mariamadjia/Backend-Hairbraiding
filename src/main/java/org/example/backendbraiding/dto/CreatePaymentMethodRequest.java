package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentMethodRequest {
    
    @NotBlank(message = "Card number is required")
    private String cardNumber;
    
    @NotBlank(message = "Expiry month is required")
    private String expMonth;
    
    @NotBlank(message = "Expiry year is required")
    private String expYear;
    
    @NotBlank(message = "CVC is required")
    private String cvc;
    
    private String cardholderName;
}
