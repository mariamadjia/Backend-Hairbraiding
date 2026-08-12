package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordTokenRequest {
    @NotBlank private String token;
    @NotBlank @Size(min = 12, max = 128) private String newPassword;
    @NotBlank private String confirmPassword;
}
