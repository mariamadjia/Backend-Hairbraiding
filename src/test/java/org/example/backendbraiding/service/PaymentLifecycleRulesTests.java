package org.example.backendbraiding.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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

    @Test
    void authorizationMustHaveADeadlineInTheFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 12, 0);
        assertTrue(PaymentLifecycleRules.isAuthorizationExpired(null, now));
        assertTrue(PaymentLifecycleRules.isAuthorizationExpired(now, now));
        assertTrue(PaymentLifecycleRules.isAuthorizationExpired(now.minusSeconds(1), now));
        assertFalse(PaymentLifecycleRules.isAuthorizationExpired(now.plusSeconds(1), now));
    }

    @Test
    void onlyTheEntireAuthorizedDepositMayBeCaptured() {
        assertTrue(PaymentLifecycleRules.isFullCapture(null, 5_000L));
        assertTrue(PaymentLifecycleRules.isFullCapture(5_000L, 5_000L));
        assertFalse(PaymentLifecycleRules.isFullCapture(4_999L, 5_000L));
        assertFalse(PaymentLifecycleRules.isFullCapture(0L, 5_000L));
    }
}
