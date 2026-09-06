package com.spendscope.categorization;

import com.spendscope.category.Category;

import java.util.Locale;
import java.util.Map;

/**
 * A categorisation run over many rows, holding the user's category table in
 * memory.
 *
 * <p>A CSV import categorises thousands of rows. Looking each category up by
 * name per row would be a textbook N+1: this loads them once and resolves from
 * a map instead.
 */
public class CategorizationSession {

    private final DescriptionNormalizer normalizer;
    private final MerchantResolver merchantResolver;
    private final Map<String, Category> categoriesByLowerCaseName;

    CategorizationSession(DescriptionNormalizer normalizer,
                          MerchantResolver merchantResolver,
                          Map<String, Category> categoriesByLowerCaseName) {
        this.normalizer = normalizer;
        this.merchantResolver = merchantResolver;
        this.categoriesByLowerCaseName = categoriesByLowerCaseName;
    }

    public String normalise(String rawDescription) {
        return normalizer.normalise(rawDescription);
    }

    /**
     * @return merchant name and resolved category id for a raw description.
     *         The category id is null when no rule matched or the rule points
     *         at a category this user cannot see.
     */
    public Categorisation categorise(String rawDescription) {
        String normalised = normalizer.normalise(rawDescription);
        MerchantMatch match = merchantResolver.resolve(normalised);
        Long categoryId = match.hasCategory() ? categoryIdFor(match.categoryName()) : null;
        return new Categorisation(normalised, match.merchant(), categoryId);
    }

    private Long categoryIdFor(String categoryName) {
        Category category = categoriesByLowerCaseName.get(categoryName.toLowerCase(Locale.ROOT));
        return category == null ? null : category.getId();
    }

    /**
     * @param normalisedDescription the comparable form of the description
     * @param merchant              canonical or guessed merchant name
     * @param categoryId            resolved category, or null if unmatched
     */
    public record Categorisation(String normalisedDescription, String merchant, Long categoryId) {
    }
}
