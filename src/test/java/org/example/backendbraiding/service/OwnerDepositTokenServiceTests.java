package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerDepositTokenServiceTests {
    @Test
    void tokenIsBoundToAppointmentAndPurpose() {
        OwnerDepositTokenService service = new OwnerDepositTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret",
                "a-very-long-test-secret-that-is-at-least-thirty-two-bytes");
        ReflectionTestUtils.setField(service, "ttl", Duration.ofHours(24));

        String token = service.issue(42L).value();

        assertTrue(service.isValid(token, 42L));
        assertFalse(service.isValid(token, 43L));
        assertFalse(service.isValid("not-a-token", 42L));
    }
}
