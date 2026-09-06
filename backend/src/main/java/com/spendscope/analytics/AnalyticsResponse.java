package com.spendscope.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of an analytics query.
 *
 * @param total              the metric's overall value for the period
 * @param previousTotal      the same metric for the comparison period, or null
 *                           when no comparison was requested
 * @param percentageChange   null whenever a percentage would be undefined -
 *                           no comparison requested, or a previous total of
 *                           zero. Never Infinity, never NaN.
 * @param points             the breakdown, empty for scalar metrics
 */
public record AnalyticsResponse(
        Metric metric,
        GroupBy groupBy,
        ResolvedPeriod period,
        ResolvedPeriod comparisonPeriod,
        BigDecimal total,
        BigDecimal previousTotal,
        BigDecimal percentageChange,
        String currency,
        List<Point> points) {

    public record ResolvedPeriod(LocalDate start, LocalDate end, long days) {

        public static ResolvedPeriod of(DateRange range) {
            return range == null ? null : new ResolvedPeriod(range.start(), range.end(), range.lengthInDays());
        }
    }

    /**
     * One bucket of a breakdown.
     *
     * @param key   a stable identifier - a category id, a merchant name, or an
     *              ISO date for time buckets
     * @param label what to show the user
     * @param share this bucket's percentage of the period total, or null when
     *              the total is zero
     */
    public record Point(
            String key,
            String label,
            BigDecimal value,
            long transactionCount,
            BigDecimal previousValue,
            BigDecimal percentageChange,
            BigDecimal share) {
    }
}
