package com.spendscope.analytics;

/**
 * What an analytics request measures.
 *
 * <p>Each metric implies the grouping it needs; {@link #impliedGroupBy()} is
 * how a request for {@code TOP_MERCHANT} gets merchant grouping without the
 * caller having to say so twice.
 */
public enum Metric {

    /** Sum of outgoing amounts in the period, reported positive. */
    TOTAL_SPENDING(null, null),

    /** Total spending divided by the number of days in the period. */
    AVERAGE_DAILY_SPENDING(null, null),

    /** How many transactions fall in the period, spending and income alike. */
    TRANSACTION_COUNT(null, null),

    /** Spending broken down by category. */
    CATEGORY_SPENDING(GroupBy.CATEGORY, null),

    /** Spending broken down by account. */
    ACCOUNT_SPENDING(GroupBy.ACCOUNT, null),

    /** The single highest-spending category. */
    TOP_CATEGORY(GroupBy.CATEGORY, 1),

    /** The single highest-spending merchant. */
    TOP_MERCHANT(GroupBy.MERCHANT, 1);

    private final GroupBy impliedGroupBy;
    private final Integer forcedLimit;

    Metric(GroupBy impliedGroupBy, Integer forcedLimit) {
        this.impliedGroupBy = impliedGroupBy;
        this.forcedLimit = forcedLimit;
    }

    /** @return the grouping this metric requires, or null if the caller chooses. */
    public GroupBy impliedGroupBy() {
        return impliedGroupBy;
    }

    /** @return a fixed result count, or null if the caller chooses. */
    public Integer forcedLimit() {
        return forcedLimit;
    }

    /** True when the metric produces a single number rather than a breakdown. */
    public boolean isScalar() {
        return impliedGroupBy == null;
    }
}
