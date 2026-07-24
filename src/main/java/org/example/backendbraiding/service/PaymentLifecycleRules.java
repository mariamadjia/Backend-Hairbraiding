package org.example.backendbraiding.service;

import java.util.Set;

final class PaymentLifecycleRules {
    private static final Set<String> RETRYABLE_CONFIRMATION_STATUSES = Set.of(
            "requires_payment_method", "requires_confirmation", "requires_action", "processing");

    private PaymentLifecycleRules() {
    }

    static boolean isReusableForConfirmation(String stripeStatus) {
        return RETRYABLE_CONFIRMATION_STATUSES.contains(stripeStatus);
    }

    static boolean isAuthorizationComplete(String stripeStatus) {
        return "requires_capture".equals(stripeStatus) || "succeeded".equals(stripeStatus);
    }
}
