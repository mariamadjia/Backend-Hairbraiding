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
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class HomepageSettingsController {
    private static final long MAX_HOMEPAGE_VIDEO_BYTES = 50L * 1024L * 1024L;
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
            defaultSettings.setBraidBookStyles("[]");
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

        String previousVideoSrc = service.getSettings()
            .map(HomepageSettingsDTO::getHeroVideoSrc)
            .orElse("");
        HomepageSettingsDTO updated = service.updateHeroVideo(heroVideoSrc, useHeroVideo, adminId);
        if (!previousVideoSrc.isBlank() && !previousVideoSrc.equals(updated.getHeroVideoSrc())) {
            deleteManagedHomepageVideo(previousVideoSrc);
        }
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/homepage-settings/footer-video")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> updateFooterVideo(
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        log.info("Updating footer video settings");

        Long adminId = extractAdminId(authentication);

        String footerVideoSrc = request.get("footerVideoSrc") != null
            ? request.get("footerVideoSrc").toString()
            : "";

        HomepageSettingsDTO updated =
            service.updateFooterVideo(footerVideoSrc, adminId);

        return ResponseEntity.ok(updated);
    }

    @PostMapping("/homepage-settings/hero-images")
    @PreAuthorize("hasRole('ADMIN')")
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

    @PostMapping("/homepage-settings/gallery-collections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> updateGalleryCollections(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long adminId = extractAdminId(authentication);
        String galleryCollections = request.get("galleryCollections") != null
                ? request.get("galleryCollections").toString()
                : "[]";
        return ResponseEntity.ok(service.updateGalleryCollections(galleryCollections, adminId));
    }

    @PostMapping("/homepage-settings/braid-book")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HomepageSettingsDTO> updateBraidBookStyles(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long adminId = extractAdminId(authentication);
        String braidBookStyles = request.get("braidBookStyles") != null
                ? request.get("braidBookStyles").toString()
                : "[]";
        return ResponseEntity.ok(service.updateBraidBookStyles(braidBookStyles, adminId));
    }

    @PostMapping("/upload/welcome-video")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadWelcomeVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String index,
            Authentication authentication) throws IOException {
        log.info("Uploading welcome video: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a video file."));
        }
        if (file.getSize() > MAX_HOMEPAGE_VIDEO_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video must be 50MB or smaller."));
        }
        String contentType = file.getContentType();
        if (!"video/mp4".equalsIgnoreCase(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only MP4 videos are supported."));
        }
        byte[] header = Arrays.copyOf(file.getBytes(), 12);
        if (header.length < 12 || header[4] != 'f' || header[5] != 't'
                || header[6] != 'y' || header[7] != 'p') {
            return ResponseEntity.badRequest().body(Map.of("error", "This file is not a valid MP4 video."));
        }
        
        String uploadDir = System.getenv("UPLOAD_DIR") != null 
            ? System.getenv("UPLOAD_DIR") 
            : "public/Gallery/uploads";
        
        // Create uploads directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique filename
        String filename = UUID.randomUUID() + ".mp4";
        Path filePath = uploadPath.resolve(filename);
        
        // Save file
        Files.copy(file.getInputStream(), filePath);

        // Return the URL
        String videoUrl = "/api/gallery/image/" + filename;
        return ResponseEntity.ok(Map.of(
            "url", videoUrl,
            "path", videoUrl,
            "videoPath", videoUrl
        ));
    }

    @DeleteMapping("/upload/welcome-video")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUnusedHomepageVideo(
            @RequestParam("path") String videoPath) throws IOException {
        String filename = Paths.get(videoPath).getFileName().toString();
        if (filename.isBlank() || !filename.matches("[0-9a-fA-F-]+\\.mp4")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid video path."));
        }
        deleteManagedHomepageVideo(videoPath);
        return ResponseEntity.ok(Map.of("message", "Unused video removed."));
    }

    private void deleteManagedHomepageVideo(String videoPath) {
        try {
            String filename = Paths.get(videoPath).getFileName().toString();
            if (!filename.matches("[0-9a-fA-F-]+\\.mp4")) {
                return;
            }
            String uploadDir = System.getenv("UPLOAD_DIR") != null
                ? System.getenv("UPLOAD_DIR")
                : "public/Gallery/uploads";
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = root.resolve(filename).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not remove replaced homepage video {}", videoPath, exception);
        }
    }
    
    private Long extractAdminId(Authentication authentication) {
        String email = authentication.getName();
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        return admin.getId();
    }
}
