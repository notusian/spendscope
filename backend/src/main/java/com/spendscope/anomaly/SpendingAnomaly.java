package com.spendscope.anomaly;

import java.math.BigDecimal;

/**
 * One category spending unusually much this month.
 *
 * @param baselineAverage  mean of the same slice of previous months
 * @param baselineMonths   how many months the baseline is built from - shown
 *                         so the user can judge how much to trust it
 * @param comparedUpToDay  the day of month both sides are measured to
 */
public record SpendingAnomaly(
        Long categoryId,
        String categoryName,
        BigDecimal currentAmount,
        BigDecimal baselineAverage,
        BigDecimal baselineStandardDeviation,
        int baselineMonths,
        BigDecimal changeAmount,
        BigDecimal changePercentage,
        int comparedUpToDay,
        AnomalySeverity severity,
        String explanation) {
}
