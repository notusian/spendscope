package com.spendscope.analytics;

import java.math.BigDecimal;

/**
 * The headline numbers for one period, in a single round trip.
 *
 * <p>A dashboard needs all of these at once; six separate endpoints would mean
 * six requests and six chances for the numbers to disagree with each other.
 */
public record AnalyticsSummaryResponse(
        AnalyticsResponse.ResolvedPeriod period,
        AnalyticsResponse.ResolvedPeriod comparisonPeriod,
        BigDecimal totalSpending,
        BigDecimal previousTotalSpending,
        BigDecimal spendingChangePercentage,
        BigDecimal averageDailySpending,
        BigDecimal totalIncome,
        BigDecimal netFlow,
        long transactionCount,
        TopEntry topCategory,
        TopEntry topMerchant,
        String currency) {

    /** @param value spending attributed to this entry, always positive */
    public record TopEntry(String key, String label, BigDecimal value) {
    }
}
