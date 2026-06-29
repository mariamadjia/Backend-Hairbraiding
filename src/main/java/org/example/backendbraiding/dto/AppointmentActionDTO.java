package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentActionDTO {
    
    @Size(max = 500, message = "Admin notes cannot exceed 500 characters")
    private String adminNotes;
}
