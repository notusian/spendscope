package com.spendscope.transaction;

/**
 * @param categoryId the new category, or null to clear it. Nullable on purpose:
 *                   "no category" is a valid, meaningful state.
 */
public record RecategoriseRequest(Long categoryId) {
}
