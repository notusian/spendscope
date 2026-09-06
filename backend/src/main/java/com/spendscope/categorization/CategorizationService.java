package com.spendscope.categorization;

import com.spendscope.category.Category;
import com.spendscope.category.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Entry point for automatic merchant detection and categorisation.
 *
 * <p>Rule based on purpose. A statistical or ML classifier needs labelled
 * training data that a new user does not have, cannot explain why it chose a
 * category, and fails in ways a user cannot correct. Rules are boring,
 * inspectable and instantly right for the merchants people actually repeat.
 * The user can always override the result, and those overrides are the data
 * that would make a learned model possible later.
 */
@Service
public class CategorizationService {

    private final DescriptionNormalizer normalizer;
    private final MerchantResolver merchantResolver;
    private final CategoryRepository categoryRepository;

    public CategorizationService(DescriptionNormalizer normalizer,
                                 MerchantResolver merchantResolver,
                                 CategoryRepository categoryRepository) {
        this.normalizer = normalizer;
        this.merchantResolver = merchantResolver;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Opens a session that has already loaded the categories this user can
     * reference. Reuse it for every row of an import.
     */
    @Transactional(readOnly = true)
    public CategorizationSession openSession(Long userId) {
        Map<String, Category> byName = new HashMap<>();
        for (Category category : categoryRepository.findAccessible(userId)) {
            // Custom categories win over a built-in of the same name, which is
            // only possible for a user who created one before the built-in
            // existed. putIfAbsent + custom-first ordering is not guaranteed,
            // so be explicit.
            String key = category.getName().toLowerCase(Locale.ROOT);
            Category existing = byName.get(key);
            if (existing == null || (existing.isBuiltIn() && !category.isBuiltIn())) {
                byName.put(key, category);
            }
        }
        return new CategorizationSession(normalizer, merchantResolver, byName);
    }

    /** Convenience for single-transaction paths (manual create/update). */
    @Transactional(readOnly = true)
    public CategorizationSession.Categorisation categoriseOne(Long userId, String rawDescription) {
        return openSession(userId).categorise(rawDescription);
    }

    /** Normalisation without any category lookup. */
    public String normalise(String rawDescription) {
        return normalizer.normalise(rawDescription);
    }
}
