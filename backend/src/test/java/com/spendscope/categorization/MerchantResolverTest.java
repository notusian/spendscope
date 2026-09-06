package com.spendscope.categorization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantResolverTest {

    private final DescriptionNormalizer normalizer = new DescriptionNormalizer();
    private MerchantResolver resolver;

    @BeforeEach
    void setUp() {
        CategorizationProperties properties = new CategorizationProperties();
        // Ordering matters and is asserted below: the specific GrabFood rule
        // must sit in front of the general Grab rule.
        properties.setRules(List.of(
                new MerchantRule("GrabFood", "Food", List.of("GRABFOOD")),
                new MerchantRule("Starbucks", "Food", List.of("STARBUCKS")),
                new MerchantRule("FairPrice", "Groceries", List.of("FAIRPRICE", "NTUC FP")),
                new MerchantRule("Grab", "Transport", List.of("GRAB")),
                new MerchantRule("Spotify", "Subscriptions", List.of("SPOTIFY"))));
        resolver = new MerchantResolver(properties);
    }

    @Test
    @DisplayName("matches a known merchant and its category")
    void matchesKnownMerchant() {
        MerchantMatch match = resolver.resolve(normalizer.normalise("STARBUCKS #4821 SINGAPORE"));
        assertThat(match.merchant()).isEqualTo("Starbucks");
        assertThat(match.categoryName()).isEqualTo("Food");
    }

    @Test
    @DisplayName("a more specific rule declared earlier wins over a general one")
    void specificRuleWinsByOrdering() {
        // Without ordering, "GRABFOOD DELIVERY" would match the GRAB substring
        // and be filed as Transport instead of Food.
        MerchantMatch food = resolver.resolve(normalizer.normalise("GRABFOOD DELIVERY"));
        assertThat(food.merchant()).isEqualTo("GrabFood");
        assertThat(food.categoryName()).isEqualTo("Food");

        MerchantMatch transport = resolver.resolve(normalizer.normalise("GRAB *TRIP 6900"));
        assertThat(transport.merchant()).isEqualTo("Grab");
        assertThat(transport.categoryName()).isEqualTo("Transport");
    }

    @Test
    @DisplayName("any one of a rule's keywords is enough")
    void matchesAnyKeyword() {
        assertThat(resolver.resolve(normalizer.normalise("NTUC FP HOLLAND")).merchant())
                .isEqualTo("FairPrice");
    }

    @Test
    @DisplayName("an unknown merchant is guessed but left uncategorised")
    void guessesUnknownMerchant() {
        // A guessed merchant still makes "top merchants" useful, but assigning
        // a category we have no evidence for would put wrong data in the user's
        // analytics. Uncategorised is the honest state.
        MerchantMatch match = resolver.resolve(normalizer.normalise("SOME LOCAL KOPITIAM 88"));
        assertThat(match.categoryName()).isNull();
        assertThat(match.hasCategory()).isFalse();
        assertThat(match.merchant()).isEqualTo("Some Local Kopitiam");
    }

    @Test
    @DisplayName("an empty description resolves to Unknown rather than throwing")
    void handlesEmptyDescription() {
        assertThat(resolver.resolve("").merchant()).isEqualTo("Unknown");
        assertThat(resolver.resolve(null).merchant()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("keywords are matched case-insensitively via the normaliser")
    void keywordsAreUpperCased() {
        CategorizationProperties properties = new CategorizationProperties();
        properties.setRules(List.of(new MerchantRule("Netflix", "Subscriptions", List.of("netflix"))));
        MerchantResolver lowerCaseRules = new MerchantResolver(properties);

        assertThat(lowerCaseRules.resolve(normalizer.normalise("Netflix.com Singapore")).merchant())
                .isEqualTo("Netflix");
    }
}
