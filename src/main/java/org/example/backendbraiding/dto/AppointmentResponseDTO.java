package org.example.backendbraiding.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponseDTO {
    private Long id;
    private CustomerDTO customer;
    private ServiceDTO service;
    private String selectedService;
    private String selectedSize;
    private String selectedLength;
    private String selectedFoundation;
    private String selectedTexture;
    private String price;
    private Integer durationMinutes;
    private LocalDateTime appointmentDateTime;
    private LocalDateTime appointmentEndDateTime;
    private String status;
    private String notes;
    private String adminNotes;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    private String paymentIntentId;
    private String paymentStatus;
    private Long depositAmount;
    private LocalDateTime paymentCapturedAt;
    private String paymentMethodLast4;
    private String paymentMethodBrand;
    private String paymentToken;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDTO {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceDTO {
        private Long id;
        private String name;
        private String description;
    }
}
