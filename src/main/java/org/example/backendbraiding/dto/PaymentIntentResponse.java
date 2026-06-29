package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentResponse {
    
    private String paymentIntentId;
    
    private String clientSecret;
    
    private String status;
    
    private Long amount;
    
    private String currency;
    
    private String message;
    
    private Long appointmentId;
}
