package org.example.backendbraiding.service;

import lombok.extern.slf4j.Slf4j;
import org.example.backendbraiding.dto.HomepageSettingsDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.HomepageSettings;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.HomepageSettingsRepository;
import org.springframework.stereotype.Service;
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

    public Optional<HomepageSettingsDTO> getSettings() {
        return repository.findAll().stream().findFirst()
            .map(this::mapToDTO);
    }

    @Transactional
    public HomepageSettingsDTO saveSettings(HomepageSettingsDTO dto, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findAll().stream().findFirst();
        
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
        settings.setFooterVideoSrc(dto.getFooterVideoSrc());
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());
        
        return mapToDTO(repository.save(settings));
    }

    @Transactional
    public HomepageSettingsDTO updateHeroVideo(String heroVideoSrc, Boolean useHeroVideo, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findAll().stream().findFirst();

        HomepageSettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new HomepageSettings();
        }

        settings.setHeroVideoSrc(heroVideoSrc);
        settings.setUseHeroVideo(useHeroVideo);
        settings.setUpdatedBy(admin.getEmail());
        settings.setUpdatedAt(LocalDateTime.now());

        return mapToDTO(repository.save(settings));
    }

    @Transactional
    public HomepageSettingsDTO updateHeroImages(String heroImages, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findAll().stream().findFirst();

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
    public HomepageSettingsDTO updateWelcomeItems(String welcomeItems, Long adminId) {
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));
        
        Optional<HomepageSettings> existing = repository.findAll().stream().findFirst();

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
    
    private HomepageSettingsDTO mapToDTO(HomepageSettings settings) {
        HomepageSettingsDTO dto = new HomepageSettingsDTO();
        dto.setHeroVideoSrc(settings.getHeroVideoSrc());
        dto.setUseHeroVideo(settings.getUseHeroVideo());
        dto.setHeroImages(settings.getHeroImages());
        dto.setWelcomeItems(settings.getWelcomeItems());
        dto.setGalleryCollections(settings.getGalleryCollections());
        dto.setFooterVideoSrc(settings.getFooterVideoSrc());
        dto.setUpdatedAt(settings.getUpdatedAt());
        dto.setUpdatedBy(settings.getUpdatedBy());
        return dto;
    }
}
