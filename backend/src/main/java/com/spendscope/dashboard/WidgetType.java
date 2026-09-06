package com.spendscope.dashboard;

import com.spendscope.analytics.Metric;

/**
 * What a dashboard card shows.
 *
 * <p>Most widget types are a saved analytics query and carry the {@link Metric}
 * they run. A few - budgets, recurring expenses, anomalies, recent activity -
 * are backed by their own endpoints instead and carry {@code null}, which is
 * how the frontend knows which API to call.
 */
public enum WidgetType {

    TOTAL_SPENDING(Metric.TOTAL_SPENDING),
    AVERAGE_DAILY_SPENDING(Metric.AVERAGE_DAILY_SPENDING),
    TRANSACTION_COUNT(Metric.TRANSACTION_COUNT),
    CATEGORY_SPENDING(Metric.CATEGORY_SPENDING),
    ACCOUNT_SPENDING(Metric.ACCOUNT_SPENDING),
    TOP_CATEGORY(Metric.TOP_CATEGORY),
    TOP_MERCHANT(Metric.TOP_MERCHANT),

    /** Total spending bucketed over time; pair with groupBy DAY/WEEK/MONTH. */
    SPENDING_TREND(Metric.TOTAL_SPENDING),

    /** Total spending grouped by merchant; pair with groupBy MERCHANT. */
    TOP_MERCHANTS(Metric.TOTAL_SPENDING),

    BUDGET_PROGRESS(null),
    RECURRING_EXPENSES(null),
    SPENDING_ANOMALIES(null),
    RECENT_TRANSACTIONS(null);

    private final Metric metric;

    WidgetType(Metric metric) {
        this.metric = metric;
    }

    public Metric metric() {
        return metric;
    }

    public boolean isAnalyticsBacked() {
        return metric != null;
    }
}
