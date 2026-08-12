package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank
    private String credential;
    private boolean rememberDevice;
}
