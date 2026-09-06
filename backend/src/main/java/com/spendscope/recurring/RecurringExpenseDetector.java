package com.spendscope.recurring;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds subscriptions and other regular payments in a transaction history.
 *
 * <p>Deterministic and explainable by design: the same input always produces
 * the same output, and every field of the result can be traced to an
 * observation ("we saw six payments, the median gap was 30 days"). A learned
 * model would need labelled data nobody has, and would not be able to tell the
 * user <em>why</em> it flagged something.
 *
 * <p>The algorithm, per merchant:
 * <ol>
 *   <li>Take the median payment amount and keep only the payments close to it.
 *       This drops the one-off SGD 4.99 in-app purchase from an otherwise
 *       steady SGD 12.98 subscription without needing an exact-amount rule.</li>
 *   <li>Require at least {@value #MIN_OCCURRENCES} payments. Two payments make
 *       a coincidence, not a pattern.</li>
 *   <li>Compute the gaps between consecutive payments and take the median gap.
 *       The median is used rather than the mean so one missed month does not
 *       drag a monthly subscription into "quarterly".</li>
 *   <li>Map that gap onto a cadence band, and measure what fraction of the gaps
 *       fall inside the band. That fraction is the consistency score.</li>
 * </ol>
 */
@Component
public class RecurringExpenseDetector {

    /** Below this, a "pattern" is indistinguishable from coincidence. */
    static final int MIN_OCCURRENCES = 3;

    /** A payment counts towards the series if within this fraction of the median. */
    private static final double AMOUNT_TOLERANCE_FRACTION = 0.20;

    /** ...or within this absolute amount, which matters for small subscriptions. */
    private static final BigDecimal AMOUNT_TOLERANCE_ABSOLUTE = new BigDecimal("1.00");

    /** Minimum fraction of gaps that must fall inside the cadence band. */
    private static final double MIN_CONSISTENCY = 0.60;

    private static final int MONTHS_PER_YEAR = 12;

    /** One observed outgoing payment. */
    public record Payment(String merchant, LocalDate date, BigDecimal amount, Long categoryId) {
    }

    /**
     * @param payments outgoing payments, amounts positive, in any order
     * @return detected recurring expenses, most expensive per month first
     */
    public List<RecurringExpense> detect(List<Payment> payments) {
        Map<String, List<Payment>> byMerchant = new LinkedHashMap<>();
        for (Payment payment : payments) {
            if (payment.merchant() == null || payment.merchant().isBlank()) {
                continue;
            }
            byMerchant.computeIfAbsent(payment.merchant(), key -> new ArrayList<>()).add(payment);
        }

        List<RecurringExpense> detected = new ArrayList<>();
        for (Map.Entry<String, List<Payment>> entry : byMerchant.entrySet()) {
            analyseMerchant(entry.getKey(), entry.getValue()).ifPresent(detected::add);
        }

        detected.sort(Comparator.comparing(RecurringExpense::monthlyCost).reversed());
        return detected;
    }

    private java.util.Optional<RecurringExpense> analyseMerchant(String merchant, List<Payment> payments) {
        if (payments.size() < MIN_OCCURRENCES) {
            return java.util.Optional.empty();
        }

        BigDecimal medianAmount = median(payments.stream().map(Payment::amount).sorted().toList());
        List<Payment> series = payments.stream()
                .filter(payment -> isCloseTo(payment.amount(), medianAmount))
                .sorted(Comparator.comparing(Payment::date))
                .toList();

        if (series.size() < MIN_OCCURRENCES) {
            return java.util.Optional.empty();
        }

        List<Long> gaps = gapsBetween(series);
        if (gaps.isEmpty()) {
            return java.util.Optional.empty();
        }

        double medianGap = median(gaps.stream().sorted().map(BigDecimal::valueOf).toList()).doubleValue();
        RecurringCadence cadence = RecurringCadence.forInterval(medianGap);
        if (cadence == null) {
            return java.util.Optional.empty();
        }

        double consistency = fractionWithinBand(gaps, cadence);
        if (consistency < MIN_CONSISTENCY) {
            return java.util.Optional.empty();
        }

        BigDecimal typicalAmount = median(series.stream().map(Payment::amount).sorted().toList());
        BigDecimal annualCost = typicalAmount.multiply(BigDecimal.valueOf(cadence.occurrencesPerYear()));
        BigDecimal monthlyCost = annualCost.divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 2, RoundingMode.HALF_UP);

        LocalDate firstSeen = series.get(0).date();
        LocalDate lastSeen = series.get(series.size() - 1).date();

        return java.util.Optional.of(new RecurringExpense(
                merchant,
                mostCommonCategoryId(series),
                cadence,
                typicalAmount.setScale(2, RoundingMode.HALF_UP),
                monthlyCost,
                annualCost.setScale(2, RoundingMode.HALF_UP),
                series.size(),
                firstSeen,
                lastSeen,
                lastSeen.plusDays(Math.round(medianGap)),
                round(consistency),
                confidenceOf(series, consistency)));
    }

    /**
     * Confidence combines three independent signals: how many payments were
     * seen, how regular the gaps were, and how stable the amount was.
     */
    private RecurringConfidence confidenceOf(List<Payment> series, double consistency) {
        double amountVariation = coefficientOfVariation(series.stream().map(Payment::amount).toList());

        if (series.size() >= 4 && consistency >= 0.80 && amountVariation <= 0.10) {
            return RecurringConfidence.HIGH;
        }
        if (series.size() >= MIN_OCCURRENCES && consistency >= 0.65 && amountVariation <= 0.25) {
            return RecurringConfidence.MEDIUM;
        }
        return RecurringConfidence.LOW;
    }

    /**
     * The category the user has most often filed this merchant under.
     *
     * <p>Most-common rather than latest: a single mis-categorised payment
     * should not relabel a two-year-old subscription.
     */
    private Long mostCommonCategoryId(List<Payment> series) {
        Map<Long, Long> countsByCategory = new LinkedHashMap<>();
        for (Payment payment : series) {
            if (payment.categoryId() != null) {
                countsByCategory.merge(payment.categoryId(), 1L, Long::sum);
            }
        }
        return countsByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<Long> gapsBetween(List<Payment> orderedSeries) {
        List<Long> gaps = new ArrayList<>(orderedSeries.size() - 1);
        for (int index = 1; index < orderedSeries.size(); index++) {
            gaps.add(ChronoUnit.DAYS.between(orderedSeries.get(index - 1).date(), orderedSeries.get(index).date()));
        }
        return gaps;
    }

    private double fractionWithinBand(List<Long> gaps, RecurringCadence cadence) {
        long inside = gaps.stream().filter(gap -> cadence.matches(gap)).count();
        return (double) inside / gaps.size();
    }

    /** Within 20% of the median, or within SGD 1, whichever is more generous. */
    private boolean isCloseTo(BigDecimal amount, BigDecimal median) {
        BigDecimal tolerance = median.abs()
                .multiply(BigDecimal.valueOf(AMOUNT_TOLERANCE_FRACTION))
                .max(AMOUNT_TOLERANCE_ABSOLUTE);
        return amount.subtract(median).abs().compareTo(tolerance) <= 0;
    }

    /** @param sorted must already be in ascending order */
    static BigDecimal median(List<BigDecimal> sorted) {
        int size = sorted.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return sorted.get(size / 2 - 1)
                .add(sorted.get(size / 2))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /**
     * Standard deviation divided by the mean - a scale-free measure of how much
     * the amount wobbles, so a SGD 300 insurance premium and a SGD 12.98
     * subscription are judged on the same footing.
     */
    static double coefficientOfVariation(List<BigDecimal> amounts) {
        if (amounts.size() < 2) {
            return 0;
        }
        double mean = amounts.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        if (mean == 0) {
            return 0;
        }
        double variance = amounts.stream()
                .mapToDouble(amount -> Math.pow(amount.doubleValue() - mean, 2))
                .sum() / amounts.size();
        return Math.sqrt(variance) / mean;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
