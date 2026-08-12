package org.example.backendbraiding.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AddOnAssignmentRequest {
    private Boolean active = true;
    @Min(0)
    private Long priceOverrideCents;
    private Boolean allSizes = true;
    private Boolean allLengths = true;
    private List<Long> serviceItemIds = new ArrayList<>();
    private List<Long> lengthOptionIds = new ArrayList<>();
}
