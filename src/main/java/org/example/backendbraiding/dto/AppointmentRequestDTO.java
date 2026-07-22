package org.example.backendbraiding.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRequestDTO {
    
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[0-9]{1,9}$", 
             message = "Phone number should be valid")
    private String phoneNumber;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "Appointment must be in the future")
    private LocalDateTime appointmentDateTime;

    @NotNull(message = "Service is required")
    private Long serviceId;

    private String selectedService;

    private String serviceName;
    
    private String selectedSize;
    
    private String selectedLength;

    private Long lengthOptionId;
    
    private String price;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}
