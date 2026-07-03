package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.HomepageSettingsDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.service.HomepageSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class HomepageSettingsController {
    private final HomepageSettingsService service;
    private final AdminRepository adminRepository;

    @GetMapping("/homepage-settings")
    public ResponseEntity<HomepageSettingsDTO> getHomepageSettings() {
        log.debug("Fetching homepage settings");
        Optional<HomepageSettingsDTO> settings = service.getSettings();
        if (settings.isPresent()) {
            return ResponseEntity.ok(settings.get());
        } else {
            HomepageSettingsDTO defaultSettings = new HomepageSettingsDTO();
            defaultSettings.setHeroVideoSrc("");
            defaultSettings.setUseHeroVideo(false);
            defaultSettings.setHeroImages("[]");
            defaultSettings.setWelcomeItems("[]");
            defaultSettings.setGalleryCollections("[]");
            defaultSettings.setFooterVideoSrc("");
            return ResponseEntity.ok(defaultSettings);
        }
    }

    @PostMapping("/homepage-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> saveHomepageSettings(
            @Valid @RequestBody HomepageSettingsDTO settings,
            Authentication authentication) {
        log.info("Saving homepage settings");
        Long adminId = extractAdminId(authentication);
        HomepageSettingsDTO saved = service.saveSettings(settings, adminId);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/homepage-settings/hero-video")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> updateHeroVideo(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        log.info("Updating hero video settings");
        Long adminId = extractAdminId(authentication);
        String heroVideoSrc = request.get("heroVideoSrc") != null ? request.get("heroVideoSrc").toString() : "";
        Boolean useHeroVideo = request.get("useHeroVideo") != null ? Boolean.valueOf(request.get("useHeroVideo").toString()) : false;

        HomepageSettingsDTO updated = service.updateHeroVideo(heroVideoSrc, useHeroVideo, adminId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/homepage-settings/hero-images")
    public ResponseEntity<HomepageSettingsDTO> updateHeroImages(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        log.info("Updating hero images");
        Long adminId = extractAdminId(authentication);
        String heroImages = request.get("heroImages") != null ? request.get("heroImages").toString() : "[]";

        HomepageSettingsDTO updated = service.updateHeroImages(heroImages, adminId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/homepage-settings/welcome-items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> updateWelcomeItems(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        log.info("Updating welcome items");
        Long adminId = extractAdminId(authentication);
        String welcomeItems = request.get("welcomeItems") != null ? request.get("welcomeItems").toString() : "[]";

        HomepageSettingsDTO updated = service.updateWelcomeItems(welcomeItems, adminId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/upload/welcome-video")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadWelcomeVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String index,
            Authentication authentication) throws IOException {
        log.info("Uploading welcome video: {}", file.getOriginalFilename());
        
        String uploadDir = System.getenv("UPLOAD_DIR") != null 
            ? System.getenv("UPLOAD_DIR") 
            : "public/Gallery/uploads";
        
        // Create uploads directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
            : ".mp4";
        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename);
        
        // Save file
        Files.copy(file.getInputStream(), filePath);
        
        // Return the URL
        String videoUrl = "/Gallery/uploads/" + filename;
        return ResponseEntity.ok(Map.of(
            "url", videoUrl,
            "path", videoUrl,
            "videoPath", videoUrl
        ));
    }
    
    private Long extractAdminId(Authentication authentication) {
        String email = authentication.getName();
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        return admin.getId();
    }
}
