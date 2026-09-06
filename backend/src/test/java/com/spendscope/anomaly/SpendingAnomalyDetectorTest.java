package com.spendscope.anomaly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingAnomalyDetectorTest {

    private final SpendingAnomalyDetector detector = new SpendingAnomalyDetector();

    private static List<BigDecimal> amounts(String... values) {
        return Stream.of(values).map(BigDecimal::new).toList();
    }

    private static SpendingAnomalyDetector.CategoryHistory history(String category,
                                                                   String current,
                                                                   String... baseline) {
        return new SpendingAnomalyDetector.CategoryHistory(
                1L, category, new BigDecimal(current), amounts(baseline), 30);
    }

    @Test
    @DisplayName("flags a clear jump in a normally steady category")
    void flagsClearIncrease() {
        List<SpendingAnomaly> anomalies = detector.detect(List.of(
                history("Food", "347.00", "210.00", "225.00", "218.00", "231.00", "215.00", "221.00")));

        assertThat(anomalies).hasSize(1);
        SpendingAnomaly food = anomalies.get(0);
        assertThat(food.categoryName()).isEqualTo("Food");
        assertThat(food.currentAmount()).isEqualByComparingTo("347.00");
        assertThat(food.baselineAverage()).isEqualByComparingTo("220.00");
        assertThat(food.changePercentage()).isEqualByComparingTo("57.7");
        assertThat(food.baselineMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("says nothing when spending is normal")
    void ignoresNormalSpending() {
        assertThat(detector.detect(List.of(
                history("Food", "228.00", "210.00", "225.00", "218.00", "231.00", "215.00", "221.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("does not flag a naturally volatile category for an ordinary swing")
    void toleratesVolatileCategories() {
        // Travel that swings between 0 and 900 has a huge standard deviation.
        // 700 is above the mean but well within its normal range, so flagging
        // it would train the user to ignore the feature.
        assertThat(detector.detect(List.of(
                history("Travel", "700.00", "0.00", "900.00", "120.00", "0.00", "640.00", "80.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("ignores a large percentage jump that is small in money terms")
    void ignoresTrivialAbsoluteIncrease() {
        // 5.00 to 20.00 is +300%, and nobody wants to be told about it.
        assertThat(detector.detect(List.of(
                history("Education", "20.00", "5.00", "5.00", "5.00", "5.00", "5.00", "5.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("ignores a small percentage rise even when the amount is large")
    void ignoresSmallRelativeIncrease() {
        assertThat(detector.detect(List.of(
                history("Groceries", "1050.00", "1000.00", "1000.00", "1000.00",
                        "1000.00", "1000.00", "1000.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("needs enough history before it calls anything unusual")
    void requiresMinimumBaseline() {
        // Two months is not enough to know what "usual" means for this user.
        assertThat(detector.detect(List.of(history("Food", "900.00", "100.00", "110.00")))).isEmpty();
    }

    @Test
    @DisplayName("brand new spending is new, not unusual")
    void ignoresNewCategories() {
        assertThat(detector.detect(List.of(
                history("Healthcare", "500.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("never reports a decrease as an anomaly")
    void ignoresDecreases() {
        assertThat(detector.detect(List.of(
                history("Shopping", "20.00", "200.00", "210.00", "190.00", "205.00", "195.00", "200.00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("marks a doubling or worse as HIGH severity")
    void gradesSeverity() {
        SpendingAnomaly high = detector.detect(List.of(
                history("Shopping", "500.00", "100.00", "110.00", "95.00", "105.00", "100.00", "98.00")))
                .get(0);
        assertThat(high.severity()).isEqualTo(AnomalySeverity.HIGH);

        SpendingAnomaly notable = detector.detect(List.of(
                history("Utilities", "185.00", "120.00", "118.00", "122.00", "119.00", "121.00", "120.00")))
                .get(0);
        assertThat(notable.severity()).isEqualTo(AnomalySeverity.NOTABLE);
    }

    @Test
    @DisplayName("explains itself in plain, non-accusatory language")
    void explainsItself() {
        SpendingAnomaly anomaly = detector.detect(List.of(
                history("Food", "347.00", "210.00", "225.00", "218.00", "231.00", "215.00", "221.00")))
                .get(0);

        // The wording must describe the numbers, not accuse the user of
        // anything. "Fraud" or "suspicious" would be a claim we cannot support.
        assertThat(anomaly.explanation())
                .contains("Food")
                .contains("higher than usual")
                .contains("347.00")
                .contains("220.00")
                .doesNotContainIgnoringCase("fraud")
                .doesNotContainIgnoringCase("suspicious");
    }

    @Test
    @DisplayName("orders anomalies by how much money the increase is")
    void ordersByAbsoluteChange() {
        List<SpendingAnomaly> anomalies = detector.detect(List.of(
                history("Utilities", "185.00", "120.00", "118.00", "122.00", "119.00", "121.00", "120.00"),
                history("Shopping", "800.00", "100.00", "110.00", "95.00", "105.00", "100.00", "98.00")));

        assertThat(anomalies).extracting(SpendingAnomaly::categoryName)
                .containsExactly("Shopping", "Utilities");
    }

    @Test
    @DisplayName("computes mean and population standard deviation")
    void computesStatistics() {
        List<BigDecimal> values = amounts("10", "20", "30", "40");
        BigDecimal mean = SpendingAnomalyDetector.mean(values);

        assertThat(mean).isEqualByComparingTo("25");
        // Population sd of [10,20,30,40] is sqrt(125) ~= 11.1803.
        assertThat(SpendingAnomalyDetector.standardDeviation(values, mean).doubleValue())
                .isCloseTo(11.1803, org.assertj.core.data.Offset.offset(0.001));
    }
}
