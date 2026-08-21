package org.example.backendbraiding.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentDepositPolicyTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void bookingRequiresExplicitDepositPolicyAcceptance() {
        AppointmentRequestDTO request = new AppointmentRequestDTO();

        request.setDepositPolicyAccepted(false);
        assertTrue(hasPolicyViolation(request));

        request.setDepositPolicyAccepted(true);
        assertFalse(hasPolicyViolation(request));
    }

    @Test
    void timezoneFreeAppointmentIsValidatedBySalonAwareServiceInsteadOfSystemClock() {
        AppointmentRequestDTO request = new AppointmentRequestDTO();
        request.setAppointmentDateTime(LocalDateTime.of(2026, 8, 20, 14, 0));
        CustomerRescheduleRequest reschedule = new CustomerRescheduleRequest();
        reschedule.setAppointmentDateTime(LocalDateTime.of(2026, 8, 20, 14, 0));

        assertFalse(validator.validate(request).stream()
                .anyMatch(violation -> "appointmentDateTime".equals(violation.getPropertyPath().toString())));
        assertFalse(validator.validate(reschedule).stream()
                .anyMatch(violation -> "appointmentDateTime".equals(violation.getPropertyPath().toString())));
    }

    private boolean hasPolicyViolation(AppointmentRequestDTO request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> "depositPolicyAccepted".equals(violation.getPropertyPath().toString()));
    }
}
