package org.example.backendbraiding.service;

import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.HomepageSettingsDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.HomepageSettings;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.HomepageSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class HomepageSettingsService {
    private final HomepageSettingsRepository repository;
    private final AdminRepository adminRepository;

    public HomepageSettingsService(HomepageSettingsRepository repository, AdminRepository adminRepository) {
        this.repository = repository;
        this.adminRepository = adminRepository;
    }

    @Cacheable("homepageSettings")
    public Optional<HomepageSettingsDTO> getSettings() {
        return repository.findFirstByOrderByIdAsc()
            .map(this::mapToDTO);
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO saveSettings(HomepageSettingsDTO dto, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findFirstByOrderByIdAsc();
        
        HomepageSettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new HomepageSettings();
        }
        
        settings.setHeroVideoSrc(dto.getHeroVideoSrc());
        settings.setUseHeroVideo(dto.getUseHeroVideo());
        settings.setHeroImages(dto.getHeroImages());
        settings.setWelcomeItems(dto.getWelcomeItems());
        settings.setGalleryCollections(dto.getGalleryCollections());
        settings.setBraidBookStyles(dto.getBraidBookStyles());
        settings.setFooterVideoSrc(dto.getFooterVideoSrc());
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());
        
        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateHeroVideo(String heroVideoSrc, Boolean useHeroVideo, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        String normalizedSource = heroVideoSrc == null ? "" : heroVideoSrc.trim();
        boolean videoEnabled = Boolean.TRUE.equals(useHeroVideo);
        if (videoEnabled && normalizedSource.isBlank()) {
            throw new IllegalArgumentException("Upload a hero video before enabling video mode.");
        }

        Optional<HomepageSettings> existing = repository.findFirstByOrderByIdAsc();

        HomepageSettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new HomepageSettings();
        }

        settings.setHeroVideoSrc(normalizedSource);
        settings.setUseHeroVideo(videoEnabled);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateHeroImages(String heroImages, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findFirstByOrderByIdAsc();

        HomepageSettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new HomepageSettings();
        }

        settings.setHeroImages(heroImages);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateWelcomeItems(String welcomeItems, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findFirstByOrderByIdAsc();

        HomepageSettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new HomepageSettings();
        }

        settings.setWelcomeItems(welcomeItems);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateFooterVideo(String footerVideoSrc, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        HomepageSettings settings = repository.findFirstByOrderByIdAsc()
            .orElseGet(HomepageSettings::new);

        settings.setFooterVideoSrc(footerVideoSrc);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateGalleryCollections(String galleryCollections, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        HomepageSettings settings = repository.findFirstByOrderByIdAsc()
            .orElseGet(HomepageSettings::new);

        settings.setGalleryCollections(galleryCollections);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    @CacheEvict(value = "homepageSettings", allEntries = true)
    public HomepageSettingsDTO updateBraidBookStyles(String braidBookStyles, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        HomepageSettings settings = repository.findFirstByOrderByIdAsc()
            .orElseGet(HomepageSettings::new);

        settings.setBraidBookStyles(braidBookStyles);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }
    
    private HomepageSettingsDTO mapToDTO(HomepageSettings settings) {
        HomepageSettingsDTO dto = new HomepageSettingsDTO();
        dto.setHeroVideoSrc(settings.getHeroVideoSrc());
        dto.setUseHeroVideo(settings.getUseHeroVideo());
        dto.setHeroImages(settings.getHeroImages());
        dto.setWelcomeItems(settings.getWelcomeItems());
        dto.setGalleryCollections(settings.getGalleryCollections());
        dto.setBraidBookStyles(settings.getBraidBookStyles());
        dto.setFooterVideoSrc(settings.getFooterVideoSrc());
        dto.setUpdatedAt(settings.getUpdatedAt());
        dto.setUpdatedBy(settings.getUpdatedBy());
        return dto;
    }
}
