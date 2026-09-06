package com.spendscope.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A detected recurring expense, with the category resolved to a name and a
 * short human-readable explanation of why it was flagged.
 */
public record RecurringExpenseResponse(
        String merchant,
        Long categoryId,
        String categoryName,
        RecurringCadence cadence,
        BigDecimal typicalAmount,
        BigDecimal monthlyCost,
        BigDecimal annualCost,
        int occurrences,
        LocalDate firstSeen,
        LocalDate lastSeen,
        LocalDate nextExpectedDate,
        RecurringConfidence confidence,
        String explanation,
        String currency) {
}
