package com.spendscope.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Percentage change between two periods.
 *
 * <p>The naive formula {@code (current - previous) / previous * 100} produces
 * {@code Infinity} or {@code NaN} whenever the previous period is zero, which
 * would then be serialised as invalid JSON. This helper returns {@code null}
 * for those cases and the API documents {@code null} as "no comparison
 * available".
 */
public final class Percentages {

    /** Percentages are reported to one decimal place, e.g. 18.2. */
    public static final int SCALE = 1;

    private Percentages() {
    }

    /**
     * @return the percentage change from {@code previous} to {@code current},
     *         or {@code null} when the change is undefined (no previous data,
     *         or a previous total of zero).
     */
    public static BigDecimal change(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) {
            return null;
        }
        if (previous.signum() == 0) {
            // Growth from zero is mathematically undefined (division by zero).
            // Reporting "+100%" or "infinite" would both be misleading, so the
            // API says "no comparison available" instead.
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @return {@code part} as a percentage of {@code total}, or {@code null}
     *         when {@code total} is zero or missing.
     */
    public static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, SCALE, RoundingMode.HALF_UP);
    }
}
