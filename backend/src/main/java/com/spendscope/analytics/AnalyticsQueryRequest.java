package com.spendscope.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The generalised analytics request behind {@code POST /api/analytics/query}.
 *
 * <p>Deliberately not a query language. Four fields - what to measure, over
 * what window, bucketed how, compared against what - cover every dashboard
 * widget in the product. Anything more expressive would need parsing,
 * validation and its own injection story for no user-visible gain.
 */
public record AnalyticsQueryRequest(
        @NotNull(message = "Metric is required")
        Metric metric,

        @NotNull(message = "Period is required")
        @Valid
        Period period,

        /** Ignored when the metric implies its own grouping. */
        GroupBy groupBy,

        /** Null is treated as {@link Comparison#NONE}. */
        Comparison comparison,

        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 100, message = "Limit must be at most 100")
        Integer limit) {

    /** Default number of buckets returned for category/merchant/account breakdowns. */
    public static final int DEFAULT_LIMIT = 10;

    public record Period(
            @NotNull(message = "Period type is required") PeriodType type,
            LocalDate start,
            LocalDate end) {
    }

    public enum Comparison {
        NONE,
        PREVIOUS_PERIOD
    }

    public Comparison comparisonOrDefault() {
        return comparison == null ? Comparison.NONE : comparison;
    }

    public int limitOrDefault() {
        if (metric.forcedLimit() != null) {
            return metric.forcedLimit();
        }
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    /** Metric-implied grouping wins over anything the caller asked for. */
    public GroupBy effectiveGroupBy() {
        return metric.impliedGroupBy() != null ? metric.impliedGroupBy() : groupBy;
    }
}
