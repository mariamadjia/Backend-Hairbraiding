package org.example.backendbraiding.dto;

import lombok.Data;

@Data
public class AdminSetupRequest {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
}
