package com.spendscope.analytics;

/**
 * How results are bucketed.
 *
 * <p>The time buckets carry the PostgreSQL {@code date_trunc} unit they map to.
 * That string reaches the database as a bound parameter and can only ever come
 * from this enum, never from a client.
 */
public enum GroupBy {

    CATEGORY(null),
    MERCHANT(null),
    ACCOUNT(null),
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String truncateUnit;

    GroupBy(String truncateUnit) {
        this.truncateUnit = truncateUnit;
    }

    public boolean isTimeBucket() {
        return truncateUnit != null;
    }

    public String truncateUnit() {
        if (truncateUnit == null) {
            throw new IllegalStateException(name() + " is not a time bucket");
        }
        return truncateUnit;
    }
}
