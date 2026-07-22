package org.example.backendbraiding.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class BookingPaymentTokenServiceTests {
    private BookingPaymentTokenService tokens;

    @BeforeEach
    void setUp() {
        tokens = new BookingPaymentTokenService();
        ReflectionTestUtils.setField(tokens, "jwtSecret",
                "test-secret-that-is-definitely-long-enough-for-hs256-signing");
    }

    @Test
    void paymentTokensAreBoundToTheAppointment() {
        String payment = tokens.createToken(42L);

        assertTrue(tokens.isValidForAppointment(payment, 42L));
        assertFalse(tokens.isValidForAppointment(payment, 43L));
    }
}
