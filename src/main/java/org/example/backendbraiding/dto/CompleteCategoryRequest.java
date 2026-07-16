package org.example.backendbraiding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompleteCategoryRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private List<Long> categoryImageIds = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<SubcategoryInput> subcategories = new ArrayList<>();

    @Data
    public static class SubcategoryInput {
        @NotBlank
        private String name;

        private List<Long> imageIds = new ArrayList<>();

        @Valid
        @NotEmpty
        private List<ServiceInput> sizes = new ArrayList<>();
    }

    @Data
    public static class ServiceInput {
        @NotBlank
        private String name;

        private List<Long> sizePhotoIds = new ArrayList<>();

        @Valid
        @NotEmpty
        private List<LengthInput> lengths = new ArrayList<>();
    }

    @Data
    public static class LengthInput {
        @NotBlank
        private String name;

        @NotBlank
        private String price;

        private String notes;
        private String duration;
        private Long imageId;
    }
}
