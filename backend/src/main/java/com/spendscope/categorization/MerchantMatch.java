package com.spendscope.categorization;

/**
 * The outcome of looking at one description.
 *
 * @param merchant     canonical merchant name, or a heuristic guess when no
 *                     rule matched; never null
 * @param categoryName the built-in category name to assign, or null when no
 *                     rule matched and the transaction should stay uncategorised
 */
public record MerchantMatch(String merchant, String categoryName) {

    public boolean hasCategory() {
        return categoryName != null;
    }
}
