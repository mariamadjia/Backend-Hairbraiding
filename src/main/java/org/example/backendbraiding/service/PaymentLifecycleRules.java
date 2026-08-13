package org.example.backendbraiding.service;

import java.util.Set;
import java.time.LocalDateTime;

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

    static boolean isAuthorizationExpired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    static boolean isFullCapture(Long requestedAmount, long amountCapturable) {
        return requestedAmount == null || requestedAmount == amountCapturable;
    }
}
