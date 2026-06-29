package org.example.backendbraiding.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomepageSettingsDTO {
    
    private String heroVideoSrc;
    
    @NotNull(message = "Use hero video flag is required")
    private Boolean useHeroVideo;
    
    private String heroImages;
    
    private String welcomeItems;
    
    private String galleryCollections;
    
    private String footerVideoSrc;
    
    private LocalDateTime updatedAt;
    private String updatedBy;
}
