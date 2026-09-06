package com.spendscope.analytics;

public enum PeriodType {

    /** First day of the current month through today. Partial by nature. */
    THIS_MONTH,

    /** The whole of the previous calendar month. */
    LAST_MONTH,

    /** A rolling 30-day window ending today. */
    LAST_30_DAYS,

    /** A rolling 90-day window ending today. */
    LAST_90_DAYS,

    /** 1 January through today. Partial by nature. */
    THIS_YEAR,

    /** Explicit start and end supplied by the caller. */
    CUSTOM;

    /**
     * Calendar-aligned periods compare against the same slice of the preceding
     * calendar period; rolling windows compare against the window immediately
     * before them. See {@link PeriodResolver#previousOf}.
     */
    public boolean isCalendarAligned() {
        return this == THIS_MONTH || this == LAST_MONTH || this == THIS_YEAR;
    }
}
