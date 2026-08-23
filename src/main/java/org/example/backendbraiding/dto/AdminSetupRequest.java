package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminSetupRequest {
    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 12, max = 128)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;
}
