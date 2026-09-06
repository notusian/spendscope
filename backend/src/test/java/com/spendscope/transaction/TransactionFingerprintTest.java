package com.spendscope.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFingerprintTest {

    private static final Long USER = 1L;
    private static final Long ACCOUNT = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);
    private static final String DESCRIPTION = "STARBUCKS";
    private static final BigDecimal AMOUNT = new BigDecimal("-7.20");
    private static final String CURRENCY = "SGD";

    private String fingerprint() {
        return TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION, AMOUNT, CURRENCY);
    }

    @Test
    @DisplayName("is stable for identical input")
    void isDeterministic() {
        assertThat(fingerprint()).isEqualTo(fingerprint());
    }

    @Test
    @DisplayName("is a 64 character hex digest")
    void isSha256Hex() {
        assertThat(fingerprint()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("ignores trailing zeros in the amount")
    void ignoresAmountScale() {
        // Two exports of the same row can write -7.2 and -7.2000. They are the
        // same transaction and must produce the same fingerprint.
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION,
                new BigDecimal("-7.2"), CURRENCY))
                .isEqualTo(TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION,
                        new BigDecimal("-7.2000"), CURRENCY));
    }

    @Test
    @DisplayName("differs when the user differs")
    void differsByUser() {
        // Without the user in the hash, two users' identical coffee purchases
        // would collide and one would be reported as a duplicate of the other.
        assertThat(TransactionFingerprint.compute(2L, ACCOUNT, DATE, DESCRIPTION, AMOUNT, CURRENCY))
                .isNotEqualTo(fingerprint());
    }

    @Test
    @DisplayName("differs when the account differs")
    void differsByAccount() {
        // The same purchase charged to a different card is a different row.
        assertThat(TransactionFingerprint.compute(USER, 99L, DATE, DESCRIPTION, AMOUNT, CURRENCY))
                .isNotEqualTo(fingerprint());
    }

    @Test
    @DisplayName("differs when the date, description, amount or currency differ")
    void differsByEveryOtherField() {
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE.plusDays(1), DESCRIPTION, AMOUNT, CURRENCY))
                .isNotEqualTo(fingerprint());
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE, "MCDONALDS", AMOUNT, CURRENCY))
                .isNotEqualTo(fingerprint());
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION, new BigDecimal("-7.21"), CURRENCY))
                .isNotEqualTo(fingerprint());
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION, AMOUNT, "USD"))
                .isNotEqualTo(fingerprint());
    }

    @Test
    @DisplayName("treats currency case-insensitively")
    void normalisesCurrencyCase() {
        assertThat(TransactionFingerprint.compute(USER, ACCOUNT, DATE, DESCRIPTION, AMOUNT, "sgd"))
                .isEqualTo(fingerprint());
    }

    @Test
    @DisplayName("field boundaries cannot be forged by shifting text between fields")
    void fieldsCannotBeShifted() {
        // Concatenating without a separator would make ("AB", "C") and
        // ("A", "BC") hash identically. The separator prevents that.
        String first = TransactionFingerprint.compute(USER, ACCOUNT, DATE, "AB", AMOUNT, "SGD");
        String second = TransactionFingerprint.compute(USER, ACCOUNT, DATE, "A", AMOUNT, "BSGD");
        assertThat(first).isNotEqualTo(second);
    }
}
