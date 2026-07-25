package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneySupportTests {
    @Test
    void parsesMoneyAtTheApiBoundaryWithoutFloatingPointMath() {
        assertEquals(35500L, MoneySupport.requirePositiveCents("$355.00", "Price"));
        assertEquals("355", MoneySupport.fromCents(35500L));
        assertEquals("0", MoneySupport.fromNonNegativeCents(0L));
    }

    @Test
    void rejectsMissingZeroNegativeAndMalformedPrices() {
        assertThrows(IllegalArgumentException.class, () -> MoneySupport.requirePositiveCents("", "Price"));
        assertThrows(IllegalArgumentException.class, () -> MoneySupport.requirePositiveCents("0", "Price"));
        assertThrows(IllegalArgumentException.class, () -> MoneySupport.requirePositiveCents("-5", "Price"));
        assertThrows(IllegalArgumentException.class, () -> MoneySupport.requirePositiveCents("free", "Price"));
    }
}
