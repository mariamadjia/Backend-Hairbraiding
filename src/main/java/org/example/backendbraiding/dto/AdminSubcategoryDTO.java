package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminSubcategoryDTO {
    private Long id;
    private String name;
    private String slug;
    private String summary;
    private String image;
    private Integer displayOrder;
    private List<AdminServiceItemDTO> items;
    private List<ImageResponse> galleryImages;
}
