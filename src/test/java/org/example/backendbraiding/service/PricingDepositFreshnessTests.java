package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingDepositFreshnessTests {

    @Test
    void calculatesCurrentDepositWithAddOnsAndAppointmentCap() {
        assertEquals(6500L, AppointmentService.effectiveDeposit(5000L, 1500L, 20000L));
        assertEquals(6000L, AppointmentService.effectiveDeposit(5000L, 1500L, 6000L));
    }

    @Test
    void rejectsAnInvalidDepositConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> AppointmentService.effectiveDeposit(0L, 0L, 20000L));
    }
}
