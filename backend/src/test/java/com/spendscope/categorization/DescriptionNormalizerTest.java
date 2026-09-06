package com.spendscope.categorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionNormalizerTest {

    private final DescriptionNormalizer normalizer = new DescriptionNormalizer();

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "STARBUCKS #4821 SINGAPORE SG | STARBUCKS SINGAPORE",
            "starbucks  coffee  4821      | STARBUCKS COFFEE",
            "POS DEBIT STARBUCKS 12/08    | STARBUCKS",
            "NTUC FAIRPRICE #171          | NTUC FAIRPRICE",
            "GRAB *TRIP 6900              | GRAB TRIP",
            "APPLE.COM/BILL ITUNES        | APPLE BILL ITUNES",
            "Café Müller                  | CAFE MULLER",
    })
    @DisplayName("strips noise, punctuation, accents and reference numbers")
    void normalises(String raw, String expected) {
        assertThat(normalizer.normalise(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("different renderings of the same merchant normalise identically")
    void sameMerchantConverges() {
        // This is what makes duplicate detection survive two exports of the
        // same statement. The realistic variations are case, spacing,
        // punctuation, transaction-type prefixes and reference numbers - not a
        // location suffix appearing in one export and not the other.
        String first = normalizer.normalise("STARBUCKS COFFEE #4821");
        String second = normalizer.normalise("POS  starbucks   coffee  4821");
        assertThat(first).isEqualTo(second).isEqualTo("STARBUCKS COFFEE");
    }

    @Test
    @DisplayName("handles null and blank input without throwing")
    void handlesEmptyInput() {
        assertThat(normalizer.normalise(null)).isEmpty();
        assertThat(normalizer.normalise("   ")).isEmpty();
    }

    @Test
    @DisplayName("falls back to the raw text when stripping would empty the string")
    void fallsBackWhenEverythingIsNoise() {
        // A description that is nothing but a reference number would otherwise
        // normalise to "", leaving nothing to fingerprint.
        assertThat(normalizer.normalise("REF 998877")).isEqualTo("REF 998877");
    }

    @Test
    @DisplayName("is deterministic")
    void isDeterministic() {
        String description = "GRABFOOD DELIVERY *ORDER 44821";
        assertThat(normalizer.normalise(description)).isEqualTo(normalizer.normalise(description));
    }

    @Test
    @DisplayName("truncates to the column width")
    void truncatesLongDescriptions() {
        assertThat(normalizer.normalise("A".repeat(900))).hasSize(500);
    }
}
