package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnerAppointmentRequest {
    @NotBlank private String quoteToken;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    @NotBlank
    @Pattern(regexp = "^[+]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[(]?[0-9]{1,4}[)]?[-\\s\\.]?[0-9]{1,9}$")
    private String phoneNumber;
    @NotNull private LocalDateTime appointmentDateTime;
    @NotNull private Long serviceId;
    private Long lengthOptionId;
    private String selectedLength;
    @Size(max = 20) private String selectedFoundation;
    @Size(max = 100) private String selectedTexture;
    @Size(max = 1000) private String notes;
    @NotNull private Boolean depositRequired;
}
