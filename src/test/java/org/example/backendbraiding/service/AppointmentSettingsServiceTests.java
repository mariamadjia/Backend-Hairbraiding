package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.AppointmentSettingsDTO;
import org.example.backendbraiding.model.Admin;
import org.example.backendbraiding.model.AppointmentSettings;
import org.example.backendbraiding.repository.AdminRepository;
import org.example.backendbraiding.repository.AppointmentSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentSettingsServiceTests {
    @Mock AppointmentSettingsRepository settingsRepository;
    @Mock AdminRepository adminRepository;
    @InjectMocks AppointmentService service;

    @Test
    void savingReturnsTheFlushedVersionAndCanBeSavedAgain() {
        AppointmentSettings settings = new AppointmentSettings();
        settings.setVersion(4L);
        when(settingsRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(settings));
        when(adminRepository.findById(1L)).thenReturn(Optional.of(new Admin()));
        when(settingsRepository.saveAndFlush(settings)).thenAnswer(call -> {
            settings.setVersion(settings.getVersion() + 1);
            return settings;
        });
        AppointmentSettingsDTO request = new AppointmentSettingsDTO(4L, 60, 60, 1, false, true, 0, "America/Chicago", null, null);
        AppointmentSettingsDTO saved = service.updateSettings(request, 1L);
        assertEquals(5L, saved.getVersion());
        assertFalse(saved.getRequireApproval());
        saved.setRequireApproval(true);
        assertEquals(6L, service.updateSettings(saved, 1L).getVersion());
        verify(settingsRepository, times(2)).saveAndFlush(settings);
    }

    @Test
    void staleSaveDoesNotChangeApprovalPolicy() {
        AppointmentSettings settings = new AppointmentSettings();
        settings.setVersion(5L);
        when(settingsRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(settings));
        AppointmentSettingsDTO request = new AppointmentSettingsDTO(4L, 60, 60, 1, false, true, 0, "America/Chicago", null, null);
        assertThrows(OptimisticLockingFailureException.class, () -> service.updateSettings(request, 1L));
        assertTrue(settings.getRequireApproval());
        verify(settingsRepository, never()).saveAndFlush(any());
    }
}
