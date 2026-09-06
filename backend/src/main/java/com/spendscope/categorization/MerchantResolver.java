package com.spendscope.categorization;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Matches a normalised description against the configured rules.
 *
 * <p>Pure logic, no database, no Spring magic beyond being a bean - which is
 * what makes it straightforward to unit test.
 */
@Component
public class MerchantResolver {

    /** Keeps the guessed merchant name short enough to display in a list. */
    private static final int MAX_GUESSED_MERCHANT_LENGTH = 40;

    private final CategorizationProperties properties;

    public MerchantResolver(CategorizationProperties properties) {
        this.properties = properties;
    }

    /**
     * @param normalisedDescription output of {@link DescriptionNormalizer}
     * @return the first matching rule's merchant and category, or a guessed
     *         merchant with no category when nothing matched
     */
    public MerchantMatch resolve(String normalisedDescription) {
        if (normalisedDescription == null || normalisedDescription.isBlank()) {
            return new MerchantMatch("Unknown", null);
        }
        return firstMatchingRule(normalisedDescription)
                .map(rule -> new MerchantMatch(rule.merchant(), rule.category()))
                .orElseGet(() -> new MerchantMatch(guessMerchant(normalisedDescription), null));
    }

    private Optional<MerchantRule> firstMatchingRule(String normalisedDescription) {
        return properties.getRules().stream()
                .filter(rule -> rule.matches(normalisedDescription))
                .findFirst();
    }

    /**
     * Fallback for merchants nobody has written a rule for.
     *
     * <p>Takes the leading words of the normalised description and title-cases
     * them. It is a guess, and it is labelled as one: the transaction is left
     * uncategorised so the user can correct it, and the guess only affects the
     * "top merchants" grouping.
     */
    private String guessMerchant(String normalisedDescription) {
        List<String> words = List.of(normalisedDescription.split(" "));
        StringBuilder guess = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (guess.length() + word.length() + 1 > MAX_GUESSED_MERCHANT_LENGTH) {
                break;
            }
            if (!guess.isEmpty()) {
                guess.append(' ');
            }
            guess.append(titleCase(word));
            // Two or three words is usually the merchant; more is address noise.
            if (guess.toString().split(" ").length >= 3) {
                break;
            }
        }
        return guess.isEmpty() ? "Unknown" : guess.toString();
    }

    private String titleCase(String word) {
        if (word.length() == 1) {
            return word;
        }
        return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
