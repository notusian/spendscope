package com.spendscope.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    @DisplayName("normalises to the column's scale")
    void normalisesScale() {
        assertThat(Money.normalise(new BigDecimal("7.2"))).isEqualTo(new BigDecimal("7.2000"));
        assertThat(Money.normalise(new BigDecimal("7.20005"))).isEqualTo(new BigDecimal("7.2001"));
    }

    @Test
    @DisplayName("accepts real ISO-4217 codes and rejects plausible-looking ones")
    void validatesCurrencyCodes() {
        assertThat(Money.isValidCurrencyCode("SGD")).isTrue();
        assertThat(Money.isValidCurrencyCode("usd")).isTrue();
        // A regex of [A-Z]{3} would wrongly accept these.
        assertThat(Money.isValidCurrencyCode("ZZZ")).isFalse();
        assertThat(Money.isValidCurrencyCode("SG")).isFalse();
        assertThat(Money.isValidCurrencyCode(null)).isFalse();
    }

    @Test
    @DisplayName("spending is the magnitude of an outflow and zero for an inflow")
    void computesSpending() {
        assertThat(Money.spendingOf(new BigDecimal("-7.20"))).isEqualByComparingTo("7.20");
        // A salary is not negative spending; it contributes nothing to a
        // spending total.
        assertThat(Money.spendingOf(new BigDecimal("4200.00"))).isEqualByComparingTo("0");
        assertThat(Money.spendingOf(null)).isEqualByComparingTo("0");
    }
}
