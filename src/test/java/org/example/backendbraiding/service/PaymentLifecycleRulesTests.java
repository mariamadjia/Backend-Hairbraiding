package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentLifecycleRulesTests {
    @Test
    void declinedIntentRemainsReusableButCanceledIntentDoesNot() {
        assertTrue(PaymentLifecycleRules.isReusableForConfirmation("requires_payment_method"));
        assertTrue(PaymentLifecycleRules.isReusableForConfirmation("requires_action"));
        assertFalse(PaymentLifecycleRules.isReusableForConfirmation("canceled"));
        assertFalse(PaymentLifecycleRules.isReusableForConfirmation("succeeded"));
    }

    @Test
    void onlyAuthorizedOrCapturedPaymentsCompleteCheckout() {
        assertTrue(PaymentLifecycleRules.isAuthorizationComplete("requires_capture"));
        assertTrue(PaymentLifecycleRules.isAuthorizationComplete("succeeded"));
        assertFalse(PaymentLifecycleRules.isAuthorizationComplete("requires_payment_method"));
    }
}
