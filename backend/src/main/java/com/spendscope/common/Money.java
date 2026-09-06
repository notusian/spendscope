package com.spendscope.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * Small helpers for monetary {@link BigDecimal} values.
 *
 * <p>Everything monetary in SpendScope is a {@code BigDecimal} scaled to
 * {@link #SCALE} decimal places, matching the {@code NUMERIC(19, 4)} columns.
 * Four places rather than two because some currencies and some interest/FX
 * figures need them, and because rounding once at display time beats rounding
 * repeatedly during aggregation.
 */
public final class Money {

    public static final int SCALE = 4;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);

    private Money() {
    }

    public static BigDecimal normalise(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** @return true when the code is a real ISO-4217 currency such as SGD or USD. */
    public static boolean isValidCurrencyCode(String code) {
        if (code == null || code.length() != 3) {
            return false;
        }
        try {
            Currency.getInstance(code.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Spending magnitude of a signed amount.
     *
     * <p>Amounts are stored signed (negative = money out). Analytics reports
     * spending as a positive number, so outflows are flipped and inflows
     * contribute zero.
     */
    public static BigDecimal spendingOf(BigDecimal signedAmount) {
        if (signedAmount == null || signedAmount.signum() >= 0) {
            return ZERO;
        }
        return signedAmount.negate();
    }
}
