package org.example.backendbraiding.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GuideSettingsDTO {
    private Boolean lengthGuideEnabled = false;
    private Boolean sizeGuideEnabled = false;
    private String lengthGuideImageUrl;
    private List<SizeGuideDTO> sizes = new ArrayList<>();

    @Data
    public static class SizeGuideDTO {
        private Long id;
        private String guideKey;
        private String displayName;
        private String imageUrl;
        private Integer displayOrder;
    }
}
