package org.example.backendbraiding.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AddOnResponse {
    private Long id;
    private Long assignmentId;
    private String name;
    private String description;
    private String pricingMode;
    private Long priceCents;
    private String depositBehavior;
    private Long depositAdjustmentCents;
    private Boolean active;
    private Integer displayOrder;
    private Long subcategoryId;
    private String subcategoryName;
    private Boolean allSizes;
    private Boolean allLengths;
    private List<Long> serviceItemIds;
    private List<Long> lengthOptionIds;
    private Boolean confirmationRequired;
}
