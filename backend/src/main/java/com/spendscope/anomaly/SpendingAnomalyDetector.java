package com.spendscope.anomaly;

import com.spendscope.common.Percentages;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Flags categories whose spending this month is well outside their own normal
 * range.
 *
 * <p>Simple and explainable on purpose. The rule is: a category is unusual when
 * it clears <em>all three</em> of these tests.
 * <ol>
 *   <li><b>Relative:</b> at least {@value #MIN_RELATIVE_INCREASE_PERCENT}%
 *       above the baseline average. Stops a 3% wobble being news.</li>
 *   <li><b>Statistical:</b> more than {@value #STANDARD_DEVIATIONS} standard
 *       deviations above the baseline. This is what stops naturally spiky
 *       categories - travel, shopping - from being flagged every month, while
 *       still catching a jump in a steady one like utilities.</li>
 *   <li><b>Absolute:</b> at least {@value #MIN_ABSOLUTE_INCREASE} more in
 *       money terms. Coffee going from 2 to 5 a month is a 150% rise and
 *       nobody cares.</li>
 * </ol>
 *
 * <p>Deliberately not machine learning. A user with three months of history has
 * no training data, an unexplainable "our model says so" is useless for a
 * money decision, and this rule can be described in one sentence in the UI.
 */
@Component
public class SpendingAnomalyDetector {

    /** Fewer months than this and "usual" is not yet a meaningful word. */
    static final int MIN_BASELINE_MONTHS = 3;

    static final int MIN_RELATIVE_INCREASE_PERCENT = 40;
    static final double STANDARD_DEVIATIONS = 1.5;
    static final BigDecimal MIN_ABSOLUTE_INCREASE = new BigDecimal("30.00");

    /** Above this multiple of the baseline, the anomaly is reported as HIGH. */
    private static final BigDecimal HIGH_SEVERITY_MULTIPLE = new BigDecimal("2.0");

    /**
     * @param categoryId        null for the uncategorised bucket
     * @param currentAmount     spending so far this month
     * @param baselineAmounts   the same slice of each previous month
     * @param comparedUpToDay   day of month both sides are measured to
     */
    public record CategoryHistory(Long categoryId,
                                  String categoryName,
                                  BigDecimal currentAmount,
                                  List<BigDecimal> baselineAmounts,
                                  int comparedUpToDay) {
    }

    public List<SpendingAnomaly> detect(List<CategoryHistory> histories) {
        List<SpendingAnomaly> anomalies = new ArrayList<>();
        for (CategoryHistory history : histories) {
            evaluate(history).ifPresent(anomalies::add);
        }
        anomalies.sort((left, right) -> right.changeAmount().compareTo(left.changeAmount()));
        return anomalies;
    }

    private java.util.Optional<SpendingAnomaly> evaluate(CategoryHistory history) {
        List<BigDecimal> baseline = history.baselineAmounts();
        if (baseline.size() < MIN_BASELINE_MONTHS) {
            return java.util.Optional.empty();
        }

        BigDecimal average = mean(baseline);
        if (average.signum() <= 0) {
            // No baseline spending at all. New spending in a brand new category
            // is not "unusual", it is just new.
            return java.util.Optional.empty();
        }

        BigDecimal current = history.currentAmount();
        BigDecimal change = current.subtract(average);
        if (change.signum() <= 0) {
            return java.util.Optional.empty();
        }

        BigDecimal changePercentage = Percentages.change(average, current);
        if (changePercentage == null
                || changePercentage.compareTo(BigDecimal.valueOf(MIN_RELATIVE_INCREASE_PERCENT)) < 0) {
            return java.util.Optional.empty();
        }
        if (change.compareTo(MIN_ABSOLUTE_INCREASE) < 0) {
            return java.util.Optional.empty();
        }

        BigDecimal standardDeviation = standardDeviation(baseline, average);
        BigDecimal threshold = average.add(
                standardDeviation.multiply(BigDecimal.valueOf(STANDARD_DEVIATIONS)));
        if (current.compareTo(threshold) <= 0) {
            return java.util.Optional.empty();
        }

        AnomalySeverity severity = current.compareTo(average.multiply(HIGH_SEVERITY_MULTIPLE)) >= 0
                ? AnomalySeverity.HIGH
                : AnomalySeverity.NOTABLE;

        return java.util.Optional.of(new SpendingAnomaly(
                history.categoryId(),
                history.categoryName(),
                scale(current),
                scale(average),
                scale(standardDeviation),
                baseline.size(),
                scale(change),
                changePercentage,
                history.comparedUpToDay(),
                severity,
                explain(history, average, current, changePercentage)));
    }

    /**
     * Plain language, no accusation.
     *
     * <p>"Higher than usual" is a description of the numbers. "Suspicious" or
     * "fraudulent" would be a claim about the user's life that this code has no
     * basis for making.
     */
    private String explain(CategoryHistory history,
                           BigDecimal average,
                           BigDecimal current,
                           BigDecimal changePercentage) {
        return "%s spending is %s%% higher than usual: %s so far this month against an average of %s over the "
                .formatted(history.categoryName(), changePercentage.toPlainString(),
                        scale(current).toPlainString(), scale(average).toPlainString())
                + "first %d days of the previous %d months."
                .formatted(history.comparedUpToDay(), history.baselineAmounts().size());
    }

    static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Population standard deviation (divide by n, not n-1).
     *
     * <p>The baseline months are the whole population being described, not a
     * sample drawn from a larger one, and with n as small as 3 the choice
     * changes the number enough to be worth stating.
     */
    static BigDecimal standardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        double meanValue = mean.doubleValue();
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - meanValue, 2))
                .sum() / values.size();
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
