package com.spendscope.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The budget arithmetic, isolated from persistence. */
class BudgetProgressTest {

    @Test
    @DisplayName("computes remaining, percentage and exceeded for a normal budget")
    void computesProgress() {
        BudgetService.Progress progress =
                BudgetService.progressOf(new BigDecimal("500.00"), new BigDecimal("420.00"));

        assertThat(progress.remaining()).isEqualByComparingTo("80.00");
        assertThat(progress.percentageUsed()).isEqualByComparingTo("84.0");
        assertThat(progress.exceeded()).isFalse();
    }

    @Test
    @DisplayName("reports an overspend as a negative remaining, not a clamped zero")
    void reportsOverspend() {
        // Clamping remaining at zero would hide the single number the user most
        // needs to see.
        BudgetService.Progress progress =
                BudgetService.progressOf(new BigDecimal("500.00"), new BigDecimal("662.00"));

        assertThat(progress.remaining()).isEqualByComparingTo("-162.00");
        assertThat(progress.percentageUsed()).isEqualByComparingTo("132.4");
        assertThat(progress.exceeded()).isTrue();
    }

    @Test
    @DisplayName("spending exactly the budget is not exceeding it")
    void exactSpendIsNotExceeded() {
        BudgetService.Progress progress =
                BudgetService.progressOf(new BigDecimal("500.00"), new BigDecimal("500.00"));

        assertThat(progress.percentageUsed()).isEqualByComparingTo("100.0");
        assertThat(progress.exceeded()).isFalse();
    }

    @Test
    @DisplayName("no spending yet is zero percent, not undefined")
    void handlesNoSpending() {
        BudgetService.Progress progress =
                BudgetService.progressOf(new BigDecimal("500.00"), BigDecimal.ZERO);

        assertThat(progress.remaining()).isEqualByComparingTo("500.00");
        assertThat(progress.percentageUsed()).isEqualByComparingTo("0.0");
        assertThat(progress.exceeded()).isFalse();
    }
}
