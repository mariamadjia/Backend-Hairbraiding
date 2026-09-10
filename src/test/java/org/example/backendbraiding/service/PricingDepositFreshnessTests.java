package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingDepositFreshnessTests {

    @Test
    void calculatesCurrentDepositWithAddOnsAndAppointmentCap() {
        assertEquals(6500L, AppointmentService.effectiveDeposit(5000L, 1500L, 20000L));
        assertEquals(6000L, AppointmentService.effectiveDeposit(5000L, 1500L, 6000L));
    }

    @Test
    void allowsAZeroDepositConfiguration() {
        assertEquals(0L, AppointmentService.effectiveDeposit(0L, 0L, 20000L));
    }
}
