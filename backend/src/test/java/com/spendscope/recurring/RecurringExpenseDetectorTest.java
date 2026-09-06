package com.spendscope.recurring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recurring detector is pure logic, so every edge case can be expressed as
 * a handful of in-memory payments.
 */
class RecurringExpenseDetectorTest {

    private final RecurringExpenseDetector detector = new RecurringExpenseDetector();

    private static RecurringExpenseDetector.Payment payment(String merchant, String date, String amount) {
        return new RecurringExpenseDetector.Payment(
                merchant, LocalDate.parse(date), new BigDecimal(amount), 6L);
    }

    /** A monthly series on the given day, with an optional per-month amount drift. */
    private static List<RecurringExpenseDetector.Payment> monthly(String merchant,
                                                                  LocalDate start,
                                                                  int months,
                                                                  String amount) {
        List<RecurringExpenseDetector.Payment> payments = new ArrayList<>();
        for (int index = 0; index < months; index++) {
            payments.add(new RecurringExpenseDetector.Payment(
                    merchant, start.plusMonths(index), new BigDecimal(amount), 6L));
        }
        return payments;
    }

    @Test
    @DisplayName("detects a textbook monthly subscription")
    void detectsMonthlySubscription() {
        List<RecurringExpense> detected = detector.detect(List.of(
                payment("Spotify", "2026-01-03", "12.98"),
                payment("Spotify", "2026-02-03", "12.98"),
                payment("Spotify", "2026-03-03", "12.98")));

        assertThat(detected).hasSize(1);
        RecurringExpense spotify = detected.get(0);
        assertThat(spotify.merchant()).isEqualTo("Spotify");
        assertThat(spotify.cadence()).isEqualTo(RecurringCadence.MONTHLY);
        assertThat(spotify.typicalAmount()).isEqualByComparingTo("12.98");
        assertThat(spotify.monthlyCost()).isEqualByComparingTo("12.98");
        assertThat(spotify.annualCost()).isEqualByComparingTo("155.76");
        assertThat(spotify.occurrences()).isEqualTo(3);
    }

    @Test
    @DisplayName("does not require exact dates - billing slips a few days")
    void toleratesDateDrift() {
        // 28 to 33 day gaps: a real subscription hitting weekends and short
        // months. Requiring exactly 30 days would find nothing in real data.
        List<RecurringExpense> detected = detector.detect(List.of(
                payment("Netflix", "2026-01-03", "19.98"),
                payment("Netflix", "2026-01-31", "19.98"),
                payment("Netflix", "2026-03-05", "19.98"),
                payment("Netflix", "2026-04-04", "19.98")));

        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).cadence()).isEqualTo(RecurringCadence.MONTHLY);
    }

    @Test
    @DisplayName("does not require exact amounts - a small price change is still the same subscription")
    void toleratesAmountDrift() {
        List<RecurringExpense> detected = detector.detect(List.of(
                payment("Singtel", "2026-01-12", "42.00"),
                payment("Singtel", "2026-02-12", "44.50"),
                payment("Singtel", "2026-03-12", "41.20"),
                payment("Singtel", "2026-04-12", "43.90")));

        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).cadence()).isEqualTo(RecurringCadence.MONTHLY);
    }

    @Test
    @DisplayName("ignores a one-off purchase mixed in with a subscription")
    void ignoresOutlierAmounts() {
        // A SGD 4.99 in-app purchase should not drag the SGD 12.98 monthly
        // series off course.
        List<RecurringExpenseDetector.Payment> payments = new ArrayList<>(
                monthly("Apple", LocalDate.parse("2026-01-08"), 6, "12.98"));
        payments.add(payment("Apple", "2026-03-19", "4.99"));

        List<RecurringExpense> detected = detector.detect(payments);

        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).typicalAmount()).isEqualByComparingTo("12.98");
        assertThat(detected.get(0).occurrences()).isEqualTo(6);
    }

    @Test
    @DisplayName("two payments are a coincidence, not a pattern")
    void requiresThreeOccurrences() {
        assertThat(detector.detect(List.of(
                payment("Spotify", "2026-01-03", "12.98"),
                payment("Spotify", "2026-02-03", "12.98")))).isEmpty();
    }

    @Test
    @DisplayName("does not flag irregular everyday spending as recurring")
    void ignoresIrregularSpending() {
        // Coffee bought whenever, at varying prices. Frequent, but not a
        // subscription - and calling it one would be worse than useless.
        assertThat(detector.detect(List.of(
                payment("Starbucks", "2026-01-03", "7.20"),
                payment("Starbucks", "2026-01-04", "12.60"),
                payment("Starbucks", "2026-01-09", "5.10"),
                payment("Starbucks", "2026-01-21", "9.80"),
                payment("Starbucks", "2026-02-02", "6.40")))).isEmpty();
    }

    @Test
    @DisplayName("detects weekly, quarterly and yearly cadences")
    void detectsOtherCadences() {
        List<RecurringExpense> weekly = detector.detect(List.of(
                payment("Cleaner", "2026-01-05", "80.00"),
                payment("Cleaner", "2026-01-12", "80.00"),
                payment("Cleaner", "2026-01-19", "80.00"),
                payment("Cleaner", "2026-01-26", "80.00")));
        assertThat(weekly).singleElement()
                .extracting(RecurringExpense::cadence).isEqualTo(RecurringCadence.WEEKLY);
        assertThat(weekly.get(0).annualCost()).isEqualByComparingTo("4160.00");

        List<RecurringExpense> quarterly = detector.detect(List.of(
                payment("Insurance", "2026-01-15", "300.00"),
                payment("Insurance", "2026-04-15", "300.00"),
                payment("Insurance", "2026-07-15", "300.00")));
        assertThat(quarterly).singleElement()
                .extracting(RecurringExpense::cadence).isEqualTo(RecurringCadence.QUARTERLY);
        assertThat(quarterly.get(0).monthlyCost()).isEqualByComparingTo("100.00");

        List<RecurringExpense> yearly = detector.detect(List.of(
                payment("Domain", "2024-02-10", "24.00"),
                payment("Domain", "2025-02-11", "24.00"),
                payment("Domain", "2026-02-09", "24.00")));
        assertThat(yearly).singleElement()
                .extracting(RecurringExpense::cadence).isEqualTo(RecurringCadence.YEARLY);
        assertThat(yearly.get(0).monthlyCost()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("a long, regular, stable series earns high confidence")
    void highConfidenceForStrongEvidence() {
        assertThat(detector.detect(monthly("Spotify", LocalDate.parse("2026-01-03"), 9, "12.98")))
                .singleElement()
                .extracting(RecurringExpense::confidence)
                .isEqualTo(RecurringConfidence.HIGH);
    }

    @Test
    @DisplayName("a short series earns at most medium confidence")
    void mediumConfidenceForWeakerEvidence() {
        assertThat(detector.detect(monthly("Adobe", LocalDate.parse("2026-01-20"), 3, "28.98")))
                .singleElement()
                .extracting(RecurringExpense::confidence)
                .isEqualTo(RecurringConfidence.MEDIUM);
    }

    @Test
    @DisplayName("predicts the next charge from the observed interval")
    void predictsNextDate() {
        List<RecurringExpense> detected =
                detector.detect(monthly("Spotify", LocalDate.parse("2026-01-03"), 4, "12.98"));

        RecurringExpense spotify = detected.get(0);
        assertThat(spotify.lastSeen()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(spotify.nextExpectedDate()).isAfter(spotify.lastSeen());
        assertThat(spotify.nextExpectedDate()).isBetween(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 8));
    }

    @Test
    @DisplayName("orders results by what they cost per month")
    void ordersByMonthlyCost() {
        List<RecurringExpenseDetector.Payment> payments = new ArrayList<>();
        payments.addAll(monthly("Spotify", LocalDate.parse("2026-01-03"), 5, "12.98"));
        payments.addAll(monthly("Netflix", LocalDate.parse("2026-01-15"), 5, "19.98"));
        payments.addAll(monthly("Gym", LocalDate.parse("2026-01-20"), 5, "128.00"));

        assertThat(detector.detect(payments))
                .extracting(RecurringExpense::merchant)
                .containsExactly("Gym", "Netflix", "Spotify");
    }

    @Test
    @DisplayName("ignores payments with no merchant")
    void ignoresMissingMerchant() {
        assertThat(detector.detect(List.of(
                new RecurringExpenseDetector.Payment(null, LocalDate.parse("2026-01-03"),
                        new BigDecimal("12.98"), null),
                new RecurringExpenseDetector.Payment("", LocalDate.parse("2026-02-03"),
                        new BigDecimal("12.98"), null)))).isEmpty();
    }

    @Test
    @DisplayName("handles an empty history")
    void handlesEmptyInput() {
        assertThat(detector.detect(List.of())).isEmpty();
    }

    @Test
    @DisplayName("attributes the category the user files the merchant under most often")
    void usesMostCommonCategory() {
        List<RecurringExpenseDetector.Payment> payments = List.of(
                new RecurringExpenseDetector.Payment("Spotify", LocalDate.parse("2026-01-03"),
                        new BigDecimal("12.98"), 6L),
                new RecurringExpenseDetector.Payment("Spotify", LocalDate.parse("2026-02-03"),
                        new BigDecimal("12.98"), 6L),
                // One mis-categorised payment must not relabel the subscription.
                new RecurringExpenseDetector.Payment("Spotify", LocalDate.parse("2026-03-03"),
                        new BigDecimal("12.98"), 99L),
                new RecurringExpenseDetector.Payment("Spotify", LocalDate.parse("2026-04-03"),
                        new BigDecimal("12.98"), 6L));

        assertThat(detector.detect(payments).get(0).categoryId()).isEqualTo(6L);
    }
}
