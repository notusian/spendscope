package com.spendscope.recurring;

/**
 * How often a recurring payment repeats.
 *
 * <p>Each cadence is a <em>band</em> of day counts, not an exact number. Real
 * subscriptions do not bill on an exact 30-day cycle: months have different
 * lengths, billing dates slip over weekends, and a card retry can add days. A
 * monthly subscription therefore matches anywhere in 25-35 days.
 */
public enum RecurringCadence {

    WEEKLY(5, 9, 52),
    FORTNIGHTLY(12, 16, 26),
    MONTHLY(25, 35, 12),
    QUARTERLY(80, 100, 4),
    YEARLY(350, 380, 1);

    private final int minDays;
    private final int maxDays;
    private final int occurrencesPerYear;

    RecurringCadence(int minDays, int maxDays, int occurrencesPerYear) {
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.occurrencesPerYear = occurrencesPerYear;
    }

    public boolean matches(double intervalInDays) {
        return intervalInDays >= minDays && intervalInDays <= maxDays;
    }

    public int occurrencesPerYear() {
        return occurrencesPerYear;
    }

    public int minDays() {
        return minDays;
    }

    public int maxDays() {
        return maxDays;
    }

    /** @return the cadence whose band contains the interval, or null if none does. */
    public static RecurringCadence forInterval(double intervalInDays) {
        for (RecurringCadence cadence : values()) {
            if (cadence.matches(intervalInDays)) {
                return cadence;
            }
        }
        return null;
    }
}
