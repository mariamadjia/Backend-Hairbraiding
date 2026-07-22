package org.example.backendbraiding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServiceItemRequest {
    private static final String PRICE_PATTERN = "^$|^\\$?\\d+(?:\\.\\d{1,2})?$";

    @NotBlank(message = "Service name is required")
    @Size(max = 120)
    private String name;

    @Pattern(regexp = PRICE_PATTERN, message = "Price must be a non-negative amount with at most two decimals")
    private String price = "";
    @Size(max = 5000) private String description = "";
    @Size(max = 1000) private String notes = "";
    @Size(max = 2000) private String image = "";
    @Size(max = 2000) private String link = "";
    @Size(max = 100) private String objectPosition = "";

    @Size(max = 30) private List<@Size(max = 2000) String> images = new ArrayList<>();
    @Size(max = 30) private List<@Size(max = 2000) String> sizePhotos = new ArrayList<>();
    @Size(max = 30) private List<@Size(max = 80) String> availableSizes = new ArrayList<>();
    @Size(max = 30) private List<@NotBlank @Size(max = 80) String> hairTextures = new ArrayList<>();
    @Valid @Size(max = 50) private List<LengthOptionInput> lengthOptions = new ArrayList<>();

    private EntityReference category;
    private EntityReference subcategory;
    private Integer displayOrder;

    @Data
    public static class EntityReference { private Long id; }

    @Data
    public static class LengthOptionInput {
        private Long id;
        @NotBlank(message = "Length name is required") @Size(max = 100) private String name;
        @NotBlank(message = "Length price is required")
        @Pattern(regexp = "^\\$?\\d+(?:\\.\\d{1,2})?$", message = "Length price must be a non-negative amount with at most two decimals")
        private String price;
        @Size(max = 1000) private String notes = "";
        @Size(max = 2000) private String imageUrl = "";
    }
}
