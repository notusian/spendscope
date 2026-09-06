package com.spendscope.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A detected recurring payment.
 *
 * @param typicalAmount      the median payment, which ignores a one-off price
 *                           change better than a mean would
 * @param monthlyCost        typical amount normalised to a month, so a yearly
 *                           and a monthly subscription can be compared
 * @param annualCost         typical amount times occurrences per year
 * @param nextExpectedDate   last seen plus the observed median interval
 */
public record RecurringExpense(
        String merchant,
        Long categoryId,
        RecurringCadence cadence,
        BigDecimal typicalAmount,
        BigDecimal monthlyCost,
        BigDecimal annualCost,
        int occurrences,
        LocalDate firstSeen,
        LocalDate lastSeen,
        LocalDate nextExpectedDate,
        double intervalConsistency,
        RecurringConfidence confidence) {
}
