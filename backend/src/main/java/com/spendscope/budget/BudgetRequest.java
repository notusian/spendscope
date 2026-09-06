package com.spendscope.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * @param month "YYYY-MM". A string rather than a date because a budget applies
 *              to a whole month, and accepting a full date would invite the
 *              question of what the day part means.
 */
public record BudgetRequest(
        @NotNull(message = "Category is required")
        Long categoryId,

        @NotNull(message = "Month is required")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Month must be in YYYY-MM format")
        String month,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 digits and 4 decimals")
        BigDecimal amount) {
}
