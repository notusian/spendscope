package com.spendscope.anomaly;

import com.spendscope.analytics.PeriodResolver;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles per-category history and hands it to the detector.
 *
 * <p>The month-to-date trick lives here: both the current month and every
 * baseline month are measured only up to today's day of the month, so a
 * comparison made on the 5th is five days against five days.
 */
@Service
public class AnomalyService {

    /** How many complete previous months form the baseline. */
    private static final int BASELINE_MONTHS = 6;

    private final TransactionRepository transactionRepository;
    private final SpendingAnomalyDetector detector;
    private final PeriodResolver periodResolver;

    public AnomalyService(TransactionRepository transactionRepository,
                          SpendingAnomalyDetector detector,
                          PeriodResolver periodResolver) {
        this.transactionRepository = transactionRepository;
        this.detector = detector;
        this.periodResolver = periodResolver;
    }

    @Transactional(readOnly = true)
    public List<SpendingAnomaly> detect(Long userId) {
        LocalDate today = periodResolver.today();
        YearMonth currentMonth = YearMonth.from(today);
        int dayOfMonth = today.getDayOfMonth();

        LocalDate windowStart = currentMonth.minusMonths(BASELINE_MONTHS).atDay(1);

        // One query for every month at once - not one per month, and not one
        // per category.
        List<Object[]> rows = transactionRepository.monthlySpendingByCategoryUpToDay(
                userId, windowStart, today, dayOfMonth);

        Map<CategoryKey, Map<YearMonth, BigDecimal>> byCategory = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long categoryId = row[0] == null ? null : ((Number) row[0]).longValue();
            String categoryName = (String) row[1];
            YearMonth month = YearMonth.from(toLocalDate(row[2]));
            BigDecimal total = (BigDecimal) row[3];

            byCategory.computeIfAbsent(new CategoryKey(categoryId, categoryName), key -> new LinkedHashMap<>())
                    .put(month, total);
        }

        List<SpendingAnomalyDetector.CategoryHistory> histories = new ArrayList<>();
        for (Map.Entry<CategoryKey, Map<YearMonth, BigDecimal>> entry : byCategory.entrySet()) {
            Map<YearMonth, BigDecimal> totalsByMonth = entry.getValue();
            BigDecimal current = totalsByMonth.getOrDefault(currentMonth, BigDecimal.ZERO);

            // A month with no spending in this category is a real zero, not a
            // gap: leaving it out would inflate the average and hide anomalies.
            List<BigDecimal> baseline = new ArrayList<>();
            for (int monthsBack = BASELINE_MONTHS; monthsBack >= 1; monthsBack--) {
                YearMonth month = currentMonth.minusMonths(monthsBack);
                baseline.add(totalsByMonth.getOrDefault(month, BigDecimal.ZERO));
            }

            histories.add(new SpendingAnomalyDetector.CategoryHistory(
                    entry.getKey().id(), entry.getKey().name(), current, baseline, dayOfMonth));
        }

        return detector.detect(histories);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private record CategoryKey(Long id, String name) {
    }
}
