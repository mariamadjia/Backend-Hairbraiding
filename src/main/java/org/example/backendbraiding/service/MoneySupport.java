package org.example.backendbraiding.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

public final class MoneySupport {
    private MoneySupport() {}

    public static OptionalLong positiveCents(String value) {
        if (value == null || value.isBlank()) return OptionalLong.empty();
        try {
            BigDecimal amount = new BigDecimal(value.replace("$", "").trim()).setScale(2, RoundingMode.HALF_UP);
            long cents = amount.movePointRight(2).longValueExact();
            return cents > 0 ? OptionalLong.of(cents) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    public static long requirePositiveCents(String value, String label) {
        return positiveCents(value).orElseThrow(() -> new IllegalArgumentException(label + " must be greater than zero"));
    }

    public static String fromCents(long cents) {
        if (cents <= 0) throw new IllegalArgumentException("Price must be greater than zero");
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString();
    }

    public static String fromNonNegativeCents(long cents) {
        if (cents < 0) throw new IllegalArgumentException("Amount cannot be negative");
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString();
    }
}
