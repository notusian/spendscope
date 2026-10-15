package com.spendscope.importjob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvValueParsersTest {

    @ParameterizedTest(name = "\"{0}\" parses as {1}")
    @CsvSource({
            "2026-08-01, 2026-08-01",
            "01/08/2026, 2026-08-01",
            "01-08-2026, 2026-08-01",
            "2026/08/01, 2026-08-01",
            "29/02/2024, 2024-02-29",
            "'  2026-08-01  ', 2026-08-01",
    })
    @DisplayName("accepts the documented date formats")
    void parsesSupportedDates(String raw, String expected) {
        assertThat(CsvValueParsers.parseDate(raw)).isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "32/01/2026", "2026-13-01", "", "   ",
            "29/02/2026", "31/04/2026", "31-04-2026", "2026/02/29"})
    @DisplayName("rejects anything it cannot parse unambiguously")
    void rejectsBadDates(String raw) {
        assertThatThrownBy(() -> CsvValueParsers.parseDate(raw)).isInstanceOf(CsvRowException.class);
    }

    @Test
    @DisplayName("does not guess between US and international date order")
    void doesNotGuessAmbiguousOrder() {
        // 03/04/2026 is read as 3 April, never as 4 March. Supporting both
        // formats would mean silently corrupting a third of a user's history.
        assertThat(CsvValueParsers.parseDate("03/04/2026")).isEqualTo(LocalDate.of(2026, 4, 3));
    }

    @ParameterizedTest(name = "\"{0}\" parses as {1}")
    @CsvSource({
            "-7.20,      -7.2000",
            "7.20,        7.2000",
            "+50,        50.0000",
            "'(7.20)',   -7.2000",
            "'$1,234.56', 1234.5600",
            "'  -12.98 ', -12.9800",
            "'€45',       45.0000",
    })
    @DisplayName("accepts the amount shapes bank exports actually produce")
    void parsesAmounts(String raw, String expected) {
        assertThat(CsvValueParsers.parseAmount(raw)).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("reads accounting parentheses as a negative")
    void parenthesesMeanNegative() {
        assertThat(CsvValueParsers.parseAmount("(120.00)")).isEqualByComparingTo("-120.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "", "   ", "12.34.56", "abc123"})
    @DisplayName("rejects unparseable amounts")
    void rejectsBadAmounts(String raw) {
        assertThatThrownBy(() -> CsvValueParsers.parseAmount(raw)).isInstanceOf(CsvRowException.class);
    }

    @Test
    @DisplayName("rejects a zero amount")
    void rejectsZero() {
        // The database has a CHECK for this; rejecting it here gives the user a
        // per-row message instead of failing the whole import.
        assertThatThrownBy(() -> CsvValueParsers.parseAmount("0.00"))
                .isInstanceOf(CsvRowException.class)
                .hasMessageContaining("must not be zero");
    }

    @Test
    @DisplayName("rejects an amount too large for the column")
    void rejectsOversizedAmount() {
        assertThatThrownBy(() -> CsvValueParsers.parseAmount("99999999999999999999.00"))
                .isInstanceOf(CsvRowException.class)
                .hasMessageContaining("too large");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00001", "-0.000049", "1e-2147483647"})
    void rejectsAmountsThatWouldRoundToZero(String raw) {
        assertThatThrownBy(() -> CsvValueParsers.parseAmount(raw))
                .isInstanceOf(CsvRowException.class)
                .hasMessageContaining("must not be zero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"999999999999999.99995", "-999999999999999.99995", "99e2147483647"})
    void rejectsAmountsThatOverflowTheStoredPrecision(String raw) {
        assertThatThrownBy(() -> CsvValueParsers.parseAmount(raw))
                .isInstanceOf(CsvRowException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void acceptsTheSmallestAndLargestStoredAmounts() {
        assertThat(CsvValueParsers.parseAmount("0.00005")).isEqualByComparingTo("0.0001");
        assertThat(CsvValueParsers.parseAmount("999999999999999.99994"))
                .isEqualByComparingTo("999999999999999.9999");
    }

    @Test
    @DisplayName("scales to the column's four decimal places")
    void scalesToColumnPrecision() {
        assertThat(CsvValueParsers.parseAmount("-7.2").scale()).isEqualTo(4);
    }
}
