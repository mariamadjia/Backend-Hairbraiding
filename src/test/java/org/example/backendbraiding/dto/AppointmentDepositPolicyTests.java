package org.example.backendbraiding.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

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

    private boolean hasPolicyViolation(AppointmentRequestDTO request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> "depositPolicyAccepted".equals(violation.getPropertyPath().toString()));
    }
}
