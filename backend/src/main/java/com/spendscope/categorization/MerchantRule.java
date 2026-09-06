package com.spendscope.categorization;

import java.util.List;

/**
 * One categorisation rule, loaded from configuration.
 *
 * <p>Rules replace what would otherwise be a long if/else chain. Adding a
 * merchant is a config edit, not a code change and redeploy.
 *
 * @param merchant the canonical display name, e.g. {@code Starbucks}
 * @param category the built-in category name this merchant maps to
 * @param keywords substrings matched against the <em>normalised</em>
 *                 description; any one of them matching is enough
 */
public record MerchantRule(String merchant, String category, List<String> keywords) {

    public MerchantRule {
        keywords = keywords == null ? List.of() : keywords.stream()
                .map(keyword -> keyword.toUpperCase(java.util.Locale.ROOT))
                .toList();
    }

    /** @param normalisedDescription output of {@link DescriptionNormalizer} */
    public boolean matches(String normalisedDescription) {
        return keywords.stream().anyMatch(normalisedDescription::contains);
    }
}
