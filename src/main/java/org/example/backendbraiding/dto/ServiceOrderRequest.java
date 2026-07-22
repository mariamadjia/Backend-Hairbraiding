package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ServiceOrderRequest {
    @NotEmpty private List<@NotNull Long> serviceIds;
}
