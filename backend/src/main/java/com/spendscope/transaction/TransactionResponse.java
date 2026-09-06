package com.spendscope.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read model for a transaction.
 *
 * <p>Carries the account and category <em>names</em> as well as their ids so a
 * list view can render without the client issuing a lookup per row.
 */
public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        LocalDate date,
        String description,
        String merchant,
        BigDecimal amount,
        String currency,
        Instant createdAt) {
}
