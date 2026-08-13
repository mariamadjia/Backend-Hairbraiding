package org.example.backendbraiding.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilitySettingsValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCompleteSalonSettings() {
        AppointmentSettingsDTO dto = validSettings();
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsUnsafeCapacityDurationAndMissingTimezone() {
        AppointmentSettingsDTO dto = validSettings();
        dto.setSlotDurationMinutes(1);
        dto.setMaxAppointmentsPerSlot(11);
        dto.setTimezone(null);
        assertFalse(validator.validate(dto).isEmpty());
    }

    private AppointmentSettingsDTO validSettings() {
        AppointmentSettingsDTO dto = new AppointmentSettingsDTO();
        dto.setVersion(0L);
        dto.setSlotDurationMinutes(60);
        dto.setAdvanceBookingDays(60);
        dto.setMaxAppointmentsPerSlot(1);
        dto.setRequireApproval(true);
        dto.setAllowSameDayBooking(true);
        dto.setBufferTimeBetweenAppointments(15);
        dto.setTimezone("America/Chicago");
        return dto;
    }
}
