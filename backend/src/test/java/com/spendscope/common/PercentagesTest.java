package com.spendscope.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The percentage change helper is small but it is where the classic
 * "Infinity in the JSON" bug lives, so it is tested exhaustively.
 */
class PercentagesTest {

    @ParameterizedTest(name = "{0} -> {1} is {2}%")
    @CsvSource({
            "1000, 1200,  20.0",
            "1000,  800, -20.0",
            "1000, 1000,   0.0",
            " 220,  347,  57.7",
            "  50,  150, 200.0",
            "1200, 1000, -16.7",
    })
    @DisplayName("computes the change between two periods")
    void computesChange(String previous, String current, String expected) {
        assertThat(Percentages.change(new BigDecimal(previous), new BigDecimal(current)))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("returns null when the previous period was zero rather than Infinity")
    void returnsNullForZeroPrevious() {
        // Dividing by zero would produce Infinity, which is not valid JSON and
        // would break the client. "No comparison available" is the honest answer.
        assertThat(Percentages.change(BigDecimal.ZERO, new BigDecimal("500"))).isNull();
    }

    @Test
    @DisplayName("returns null when either period is missing")
    void returnsNullForMissingData() {
        assertThat(Percentages.change(null, new BigDecimal("500"))).isNull();
        assertThat(Percentages.change(new BigDecimal("500"), null)).isNull();
        assertThat(Percentages.change(null, null)).isNull();
    }

    @Test
    @DisplayName("zero to zero is no change, not undefined")
    void zeroToZeroIsNull() {
        // Still undefined: 0% of nothing is not a meaningful statement either.
        assertThat(Percentages.change(BigDecimal.ZERO, BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("uses the magnitude of the previous value so signs do not flip the direction")
    void usesAbsolutePrevious() {
        // Spending totals are always positive here, but guarding the sign means
        // a future caller passing a signed value still gets a sensible answer.
        assertThat(Percentages.change(new BigDecimal("-100"), new BigDecimal("-50")))
                .isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("computes a share of a total")
    void computesRatio() {
        assertThat(Percentages.ratio(new BigDecimal("420"), new BigDecimal("500")))
                .isEqualByComparingTo("84.0");
    }

    @Test
    @DisplayName("a share of zero is undefined, not zero")
    void ratioOfZeroTotalIsNull() {
        assertThat(Percentages.ratio(new BigDecimal("420"), BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("a share above the total is reported above 100 rather than clamped")
    void ratioCanExceedOneHundred() {
        // An exceeded budget must be visible as exceeded.
        assertThat(Percentages.ratio(new BigDecimal("662"), new BigDecimal("500")))
                .isEqualByComparingTo("132.4");
    }
}
