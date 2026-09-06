package com.spendscope.transaction;

import com.spendscope.common.validation.CurrencyCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a transaction.
 *
 * @param amount   signed: negative for spending, positive for income.
 *                 {@code @Digits} keeps it inside the NUMERIC(19,4) column so
 *                 an oversized value fails validation rather than the database.
 * @param currency optional; defaults to the account's currency.
 * @param merchant optional; when omitted the merchant is derived from the
 *                 description by the categorisation rules.
 */
public record TransactionRequest(
        @NotNull(message = "Account is required")
        Long accountId,

        Long categoryId,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Amount is required")
        @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 digits and 4 decimals")
        BigDecimal amount,

        @CurrencyCode(optional = true)
        String currency,

        @Size(max = 120, message = "Merchant must be at most 120 characters")
        String merchant) {
}
