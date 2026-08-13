package org.example.backendbraiding.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeWebhookControllerTests {
    @Test
    void extractsPaymentIntentIdFromVersionIndependentRawEventObject() {
        assertEquals("pi_test_123", StripeWebhookController.paymentIntentIdFromRawJson(
                "{\"id\":\"pi_test_123\",\"object\":\"payment_intent\",\"future_field\":true}"));
    }

    @Test
    void rejectsAnUnexpectedRawEventObject() {
        assertThrows(IllegalStateException.class,
                () -> StripeWebhookController.paymentIntentIdFromRawJson("{\"id\":\"ch_test_123\"}"));
    }
}
