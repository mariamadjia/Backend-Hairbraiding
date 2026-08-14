package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.GuideSettingsDTO;
import org.example.backendbraiding.model.GuideSettings;
import org.example.backendbraiding.model.SizeGuideProfile;
import org.example.backendbraiding.repository.GuideSettingsRepository;
import org.example.backendbraiding.repository.SizeGuideProfileRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class GuideSettingsService {
    private final GuideSettingsRepository settingsRepository;
    private final SizeGuideProfileRepository profileRepository;

    @Cacheable("guideSettings")
    @Transactional(readOnly = true)
    public GuideSettingsDTO get() {
        GuideSettings settings = settingsRepository.findById(1L).orElseGet(GuideSettings::new);
        return map(settings, profileRepository.findAllByOrderByDisplayOrderAscIdAsc());
    }

    @Transactional
    @CacheEvict(value = "guideSettings", allEntries = true)
    public GuideSettingsDTO update(GuideSettingsDTO request) {
        GuideSettings settings = settingsRepository.findByIdForUpdate(1L).orElseGet(GuideSettings::new);
        String lengthImage = cleanUrl(request.getLengthGuideImageUrl());
        settings.setLengthGuideImageUrl(lengthImage);
        settings.setLengthGuideEnabled(Boolean.TRUE.equals(request.getLengthGuideEnabled()) && lengthImage != null);
        settings.setSizeGuideEnabled(Boolean.TRUE.equals(request.getSizeGuideEnabled()));
        settingsRepository.save(settings);

        Map<Long, GuideSettingsDTO.SizeGuideDTO> requested = new HashMap<>();
        if (request.getSizes() != null) for (var size : request.getSizes()) if (size.getId() != null) requested.put(size.getId(), size);
        List<SizeGuideProfile> profiles = profileRepository.findAllByOrderByDisplayOrderAscIdAsc();
        List<SizeGuideProfile> changedProfiles = new ArrayList<>();
        for (SizeGuideProfile profile : profiles) {
            var update = requested.get(profile.getId());
            if (update != null) {
                String nextImageUrl = cleanUrl(update.getImageUrl());
                if (!Objects.equals(profile.getImageUrl(), nextImageUrl)) {
                    profile.setImageUrl(nextImageUrl);
                    changedProfiles.add(profile);
                }
            }
        }
        if (!changedProfiles.isEmpty()) profileRepository.saveAll(changedProfiles);
        return map(settings, profiles);
    }

    private String cleanUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 2000 || !(result.startsWith("/api/gallery/image/") || result.startsWith("https://") || result.startsWith("http://"))) {
            throw new IllegalArgumentException("Invalid guide image URL");
        }
        return result;
    }

    private GuideSettingsDTO map(GuideSettings settings, List<SizeGuideProfile> profiles) {
        GuideSettingsDTO dto = new GuideSettingsDTO();
        dto.setLengthGuideEnabled(Boolean.TRUE.equals(settings.getLengthGuideEnabled()));
        dto.setSizeGuideEnabled(Boolean.TRUE.equals(settings.getSizeGuideEnabled()));
        dto.setLengthGuideImageUrl(settings.getLengthGuideImageUrl());
        dto.setSizes(profiles.stream().map(profile -> {
            var size = new GuideSettingsDTO.SizeGuideDTO();
            size.setId(profile.getId()); size.setGuideKey(profile.getGuideKey()); size.setDisplayName(profile.getDisplayName());
            size.setImageUrl(profile.getImageUrl()); size.setDisplayOrder(profile.getDisplayOrder()); return size;
        }).toList());
        return dto;
    }
}
