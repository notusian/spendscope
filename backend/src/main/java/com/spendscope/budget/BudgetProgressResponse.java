package com.spendscope.budget;

import java.math.BigDecimal;

/**
 * A budget together with how it is going.
 *
 * @param spent           always derived from transactions, never stored
 * @param remaining       negative once the budget is exceeded, so the client
 *                        can show an overspend rather than clamping at zero
 * @param percentageUsed  can exceed 100; null only if the budget were zero,
 *                        which the schema forbids
 * @param exceeded        convenience flag so three clients do not each
 *                        re-derive the same comparison
 */
public record BudgetProgressResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String month,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentageUsed,
        boolean exceeded,
        String currency) {
}
