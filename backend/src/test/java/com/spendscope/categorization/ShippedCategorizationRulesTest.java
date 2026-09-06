package com.spendscope.categorization;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the rules that actually ship in application.yml, not a hand-written
 * fixture.
 *
 * <p>This exists because of a real bug. The normaliser strips noise tokens, so
 * a rule keyword containing one of them - "APPLE COM BILL", where COM is
 * stripped - could never match anything, and nothing failed. Config was dead
 * and silent.
 *
 * <p>Two guards are asserted here:
 * <ol>
 *   <li>No shipped keyword contains a token the normaliser removes.</li>
 *   <li>Realistic bank descriptions resolve to the merchant and category a user
 *       would expect, with the specific-before-general ordering intact.</li>
 * </ol>
 */
class ShippedCategorizationRulesTest {

    /** Mirrors DescriptionNormalizer.NOISE_TOKENS. */
    private static final List<String> NOISE_TOKENS = List.of(
            "POS", "PURCHASE", "PAYMENT", "DEBIT", "CREDIT", "CARD", "TRANSACTION", "TXN", "REF",
            "VISA", "MASTERCARD", "NETS", "PAYNOW", "GIRO", "IBANKING", "TRANSFER", "WWW", "COM",
            "SG", "SGP", "PTE", "LTD", "LLC", "INC");

    private static final DescriptionNormalizer NORMALIZER = new DescriptionNormalizer();
    private static CategorizationProperties properties;
    private static MerchantResolver resolver;

    @BeforeAll
    static void loadShippedRules() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties loaded = yaml.getObject();
        assertThat(loaded).isNotNull();

        Map<String, Object> asMap = new HashMap<>();
        loaded.forEach((key, value) -> asMap.put(String.valueOf(key), value));

        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("application.yml", asMap));

        properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("spendscope.categorization", Bindable.of(CategorizationProperties.class))
                .orElseThrow(() -> new IllegalStateException("No categorization rules found in application.yml"));
        resolver = new MerchantResolver(properties);
    }

    @Test
    @DisplayName("the shipped configuration actually contains rules")
    void rulesAreLoaded() {
        assertThat(properties.getRules()).hasSizeGreaterThan(30);
    }

    @Test
    @DisplayName("no keyword contains a token the normaliser strips")
    void noKeywordIsUnmatchable() {
        // A keyword containing stripped noise is dead config: it can never
        // appear in a normalised description, so the rule silently never fires.
        for (MerchantRule rule : properties.getRules()) {
            for (String keyword : rule.keywords()) {
                for (String token : keyword.split(" ")) {
                    assertThat(NOISE_TOKENS)
                            .as("rule '%s' keyword '%s' contains stripped token '%s' and can never match",
                                    rule.merchant(), keyword, token)
                            .doesNotContain(token);
                }
            }
        }
    }

    @Test
    @DisplayName("every rule points at a real built-in category")
    void everyRuleTargetsAKnownCategory() {
        // Mirrors the categories seeded by V3__create_categories.sql.
        List<String> builtInCategories = List.of("food", "groceries", "transport", "shopping",
                "entertainment", "subscriptions", "utilities", "healthcare", "travel", "education",
                "income", "other");

        for (MerchantRule rule : properties.getRules()) {
            assertThat(builtInCategories)
                    .as("rule '%s' points at category '%s', which is not a built-in category",
                            rule.merchant(), rule.category())
                    .contains(rule.category().toLowerCase(Locale.ROOT));
        }
    }

    @ParameterizedTest(name = "\"{0}\" -> {1} / {2}")
    @CsvSource(delimiter = '|', value = {
            "STARBUCKS COFFEE #4821            | Starbucks          | Food",
            "MCDONALDS RESTAURANT              | McDonald's         | Food",
            "GRABFOOD DELIVERY                 | GrabFood           | Food",
            "GRAB *TRIP 6900                   | Grab               | Transport",
            "SIMPLYGO TRANSIT                  | SimplyGo           | Transport",
            "NTUC FAIRPRICE #171               | FairPrice          | Groceries",
            "COLD STORAGE HOLLAND V            | Cold Storage       | Groceries",
            "SHENG SIONG SUPERMARKET           | Sheng Siong        | Groceries",
            "SPOTIFY P34A9B21                  | Spotify            | Subscriptions",
            "NETFLIX.COM SINGAPORE             | Netflix            | Subscriptions",
            "APPLE.COM/BILL ITUNES             | Apple              | Subscriptions",
            "ADOBE CREATIVE CLOUD              | Adobe              | Subscriptions",
            "UNIQLO SINGAPORE                  | Uniqlo             | Shopping",
            "SHOPEE SG PAYMENT                 | Shopee             | Shopping",
            "AMAZON SG MARKETPLACE             | Amazon             | Shopping",
            "LAZADA SINGAPORE                  | Lazada             | Shopping",
            "SP SERVICES LTD                   | SP Group           | Utilities",
            "SINGTEL MOBILE POSTPAID           | Singtel            | Utilities",
            "GOLDEN VILLAGE CINEMAS            | Golden Village     | Entertainment",
            "STEAMGAMES.COM                    | Steam              | Entertainment",
            "GUARDIAN HEALTH & BEAUTY          | Guardian           | Healthcare",
            "SINGAPORE AIRLINES TICKET         | Singapore Airlines | Travel",
            "AGODA HOTEL BOOKING               | Agoda              | Travel",
            "AIRBNB * HMQ4T2                   | Airbnb             | Travel",
            "BOOKING.COM RESERVATION           | Booking.com        | Travel",
            "DELIVEROO SG                      | Deliveroo          | Food",
            "YA KUN KAYA TOAST                 | Ya Kun Kaya Toast  | Food",
            "TOAST BOX ORCHARD                 | Toast Box          | Food",
            "KFC SINGAPORE                     | KFC                | Food",
            "MUJI PLAZA SING                   | Muji               | Shopping",
            "DECATHLON SG                      | Decathlon          | Shopping",
    })
    @DisplayName("realistic bank descriptions resolve to the expected merchant and category")
    void resolvesRealisticDescriptions(String rawDescription, String expectedMerchant, String expectedCategory) {
        MerchantMatch match = resolver.resolve(NORMALIZER.normalise(rawDescription));

        assertThat(match.merchant()).isEqualTo(expectedMerchant.trim());
        assertThat(match.categoryName()).isEqualTo(expectedCategory.trim());
    }

    @Test
    @DisplayName("a hotel booking is attributed to the hotel site, not to Booking.com")
    void specificTravelBrandsBeatTheGenericBookingKeyword() {
        assertThat(resolver.resolve(NORMALIZER.normalise("AGODA HOTEL BOOKING")).merchant())
                .isEqualTo("Agoda");
    }

    @Test
    @DisplayName("an unknown merchant stays uncategorised")
    void unknownMerchantsAreNotGuessedIntoACategory() {
        assertThat(resolver.resolve(NORMALIZER.normalise("SOME LOCAL KOPITIAM 88")).categoryName())
                .isNull();
    }
}
