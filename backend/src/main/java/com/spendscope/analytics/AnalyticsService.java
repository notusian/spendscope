package com.spendscope.analytics;

import com.spendscope.account.Account;
import com.spendscope.account.AccountRepository;
import com.spendscope.common.Money;
import com.spendscope.common.Percentages;
import com.spendscope.common.error.BadRequestException;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The reusable analytics layer.
 *
 * <p>Everything the dashboard shows is expressed as a
 * {@link AnalyticsQueryRequest}: pick a metric, a period, a grouping and
 * optionally a comparison. Widgets are then just saved requests, which is why
 * a user can reconfigure a widget without any new backend code.
 *
 * <p>All aggregation happens in PostgreSQL. Pulling a year of transactions into
 * the JVM to sum them would work at 1,000 rows and fall over at 100,000.
 */
@Service
public class AnalyticsService {

    /** Fallback when a user has no accounts yet and therefore no currency. */
    private static final String DEFAULT_CURRENCY = "SGD";

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PeriodResolver periodResolver;

    public AnalyticsService(TransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            PeriodResolver periodResolver) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.periodResolver = periodResolver;
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    /** The headline metrics for one period, with a previous-period comparison. */
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Long userId, PeriodType periodType, LocalDate start, LocalDate end) {
        DateRange current = periodResolver.resolve(periodType, start, end);
        DateRange previous = periodResolver.previousOf(periodType, current);

        BigDecimal totalSpending = spending(userId, current);
        BigDecimal previousSpending = spending(userId, previous);
        BigDecimal income = Money.zeroIfNull(transactionRepository.sumIncome(userId, current.start(), current.end()));

        List<AnalyticsResponse.Point> categories = groupedPoints(userId, current, GroupBy.CATEGORY, 1);
        List<AnalyticsResponse.Point> merchants = groupedPoints(userId, current, GroupBy.MERCHANT, 1);

        return new AnalyticsSummaryResponse(
                AnalyticsResponse.ResolvedPeriod.of(current),
                AnalyticsResponse.ResolvedPeriod.of(previous),
                totalSpending,
                previousSpending,
                Percentages.change(previousSpending, totalSpending),
                averageDaily(totalSpending, current),
                income,
                income.subtract(totalSpending),
                transactionRepository.countInPeriod(userId, current.start(), current.end()),
                topEntry(categories),
                topEntry(merchants),
                currencyFor(userId));
    }

    // ------------------------------------------------------------------
    // Generalised query
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AnalyticsResponse query(Long userId, AnalyticsQueryRequest request) {
        if (request.groupBy() != null && (request.metric() == Metric.TRANSACTION_COUNT
                || request.metric() == Metric.AVERAGE_DAILY_SPENDING)) {
            throw new BadRequestException("This metric returns a single value and does not support grouping");
        }
        DateRange current = periodResolver.resolve(
                request.period().type(), request.period().start(), request.period().end());

        boolean compare = request.comparisonOrDefault() == AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD;
        DateRange previous = compare ? periodResolver.previousOf(request.period().type(), current) : null;

        BigDecimal total = scalarValue(userId, request.metric(), current);
        BigDecimal previousTotal = previous == null ? null : scalarValue(userId, request.metric(), previous);

        GroupBy groupBy = request.effectiveGroupBy();
        List<AnalyticsResponse.Point> points = groupBy == null
                ? List.of()
                : withComparison(
                        groupedPoints(userId, current, groupBy, request.limitOrDefault()),
                        previous == null ? null : groupedPoints(userId, previous, groupBy, Integer.MAX_VALUE),
                        spending(userId, current), groupBy);

        return new AnalyticsResponse(
                request.metric(),
                groupBy,
                AnalyticsResponse.ResolvedPeriod.of(current),
                AnalyticsResponse.ResolvedPeriod.of(previous),
                total,
                previousTotal,
                Percentages.change(previousTotal, total),
                currencyFor(userId),
                points);
    }

    // ------------------------------------------------------------------
    // Metric evaluation
    // ------------------------------------------------------------------

    private BigDecimal scalarValue(Long userId, Metric metric, DateRange range) {
        return switch (metric) {
            case TOTAL_SPENDING, CATEGORY_SPENDING, ACCOUNT_SPENDING, TOP_CATEGORY, TOP_MERCHANT ->
                    spending(userId, range);
            case AVERAGE_DAILY_SPENDING -> averageDaily(spending(userId, range), range);
            case TRANSACTION_COUNT ->
                    BigDecimal.valueOf(transactionRepository.countInPeriod(userId, range.start(), range.end()));
        };
    }

    private BigDecimal spending(Long userId, DateRange range) {
        return Money.zeroIfNull(transactionRepository.sumSpending(userId, range.start(), range.end()));
    }

    /**
     * Spending divided by the number of days in the period.
     *
     * <p>Uses the period length rather than the number of days that actually
     * had transactions, so a quiet week correctly lowers the daily average
     * instead of being invisible.
     */
    private BigDecimal averageDaily(BigDecimal totalSpending, DateRange range) {
        long days = range.lengthInDays();
        if (days <= 0) {
            return Money.ZERO;
        }
        return totalSpending.divide(BigDecimal.valueOf(days), Money.SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Grouping
    // ------------------------------------------------------------------

    private List<AnalyticsResponse.Point> groupedPoints(Long userId, DateRange range, GroupBy groupBy, int limit) {
        List<AnalyticsResponse.Point> points = switch (groupBy) {
            case CATEGORY -> transactionRepository
                    .spendingByCategory(userId, range.start(), range.end()).stream()
                    .map(row -> point(
                            SqlRows.asString(SqlRows.asLong(row[0])),
                            SqlRows.asString(row[1]),
                            SqlRows.asBigDecimal(row[2]),
                            SqlRows.asPrimitiveLong(row[3])))
                    .toList();
            case MERCHANT -> transactionRepository
                    .spendingByMerchant(userId, range.start(), range.end()).stream()
                    .map(row -> point(
                            SqlRows.asString(row[0]),
                            SqlRows.asString(row[0]),
                            SqlRows.asBigDecimal(row[1]),
                            SqlRows.asPrimitiveLong(row[2])))
                    .toList();
            case ACCOUNT -> transactionRepository
                    .spendingByAccount(userId, range.start(), range.end()).stream()
                    .map(row -> point(
                            SqlRows.asString(SqlRows.asLong(row[0])),
                            SqlRows.asString(row[1]),
                            SqlRows.asBigDecimal(row[2]),
                            SqlRows.asPrimitiveLong(row[3])))
                    .toList();
            case DAY, WEEK, MONTH -> timeBuckets(userId, range, groupBy);
        };

        // The limit exists to cap a "top N categories/merchants" breakdown.
        // Applying it to a time series would silently truncate a 30-day chart
        // to its first 10 days, which looks like missing data rather than a
        // setting. A time series is already bounded by its period.
        if (groupBy.isTimeBucket() || limit >= points.size()) {
            return points;
        }
        return points.subList(0, Math.max(limit, 0));
    }

    /**
     * Time buckets are returned dense: a week with no spending appears as a
     * zero rather than as a gap, so a line chart does not silently connect
     * across missing periods.
     */
    private List<AnalyticsResponse.Point> timeBuckets(Long userId, DateRange range, GroupBy groupBy) {
        Map<LocalDate, AnalyticsResponse.Point> byBucket = new LinkedHashMap<>();
        for (Object[] row : transactionRepository.spendingByTimeBucket(
                userId, range.start(), range.end(), groupBy.truncateUnit())) {
            LocalDate bucket = SqlRows.asLocalDate(row[0]);
            byBucket.put(bucket, point(
                    bucket.format(ISO_DATE),
                    bucketLabel(bucket, groupBy),
                    SqlRows.asBigDecimal(row[1]),
                    SqlRows.asPrimitiveLong(row[2])));
        }

        List<AnalyticsResponse.Point> dense = new ArrayList<>();
        for (LocalDate bucket : bucketStarts(range, groupBy)) {
            dense.add(byBucket.getOrDefault(bucket,
                    point(bucket.format(ISO_DATE), bucketLabel(bucket, groupBy), Money.ZERO, 0)));
        }
        return dense;
    }

    /** Every bucket start between the range ends, aligned the way date_trunc aligns them. */
    private List<LocalDate> bucketStarts(DateRange range, GroupBy groupBy) {
        List<LocalDate> starts = new ArrayList<>();
        LocalDate cursor = truncate(range.start(), groupBy);
        while (!cursor.isAfter(range.end())) {
            starts.add(cursor);
            cursor = switch (groupBy) {
                case DAY -> cursor.plusDays(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
                default -> throw new IllegalStateException("Not a time bucket: " + groupBy);
            };
        }
        return starts;
    }

    /** Mirrors PostgreSQL's date_trunc, whose weeks start on Monday. */
    private LocalDate truncate(LocalDate date, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> date;
            case WEEK -> date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            default -> throw new IllegalStateException("Not a time bucket: " + groupBy);
        };
    }

    private String bucketLabel(LocalDate bucket, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> bucket.getDayOfMonth() + " "
                    + bucket.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            case WEEK -> "Week of " + bucket.getDayOfMonth() + " "
                    + bucket.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            case MONTH -> bucket.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + bucket.getYear();
            default -> bucket.format(ISO_DATE);
        };
    }

    /**
     * Categories, accounts and merchants join by identity. Time series align
     * by bucket position within each period because their absolute dates differ.
     * If the previous period has fewer buckets, unmatched comparisons stay null.
     */
    private List<AnalyticsResponse.Point> withComparison(List<AnalyticsResponse.Point> current,
                                                         List<AnalyticsResponse.Point> previous,
                                                         BigDecimal periodTotal,
                                                         GroupBy groupBy) {
        Map<String, BigDecimal> previousByKey = new LinkedHashMap<>();
        if (previous != null) {
            previous.forEach(point -> previousByKey.put(point.key(), point.value()));
        }

        List<AnalyticsResponse.Point> enriched = new ArrayList<>(current.size());
        for (int index = 0; index < current.size(); index++) {
            AnalyticsResponse.Point point = current.get(index);
            BigDecimal previousValue = null;
            if (previous != null) {
                previousValue = groupBy.isTimeBucket()
                        ? (index < previous.size() ? previous.get(index).value() : null)
                        : previousByKey.getOrDefault(point.key(), Money.ZERO);
            }
            enriched.add(new AnalyticsResponse.Point(
                    point.key(),
                    point.label(),
                    point.value(),
                    point.transactionCount(),
                    previousValue,
                    Percentages.change(previousValue, point.value()),
                    Percentages.ratio(point.value(), periodTotal)));
        }
        return enriched;
    }

    private AnalyticsResponse.Point point(String key, String label, BigDecimal value, long count) {
        return new AnalyticsResponse.Point(key, label, value, count, null, null, null);
    }

    private AnalyticsSummaryResponse.TopEntry topEntry(List<AnalyticsResponse.Point> points) {
        if (points.isEmpty()) {
            return null;
        }
        AnalyticsResponse.Point top = points.stream()
                .max(Comparator.comparing(AnalyticsResponse.Point::value))
                .orElseThrow();
        return new AnalyticsSummaryResponse.TopEntry(top.key(), top.label(), top.value());
    }

    /**
     * The currency to label totals with.
     *
     * <p>Known limitation, stated rather than hidden: totals are a plain sum of
     * the underlying amounts with no FX conversion. A user whose accounts are
     * all in one currency - the overwhelmingly common case - gets correct
     * numbers; a genuinely multi-currency user would need rates, which is
     * listed under future improvements.
     */
    private String currencyFor(Long userId) {
        List<Account> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId);
        if (accounts.isEmpty()) {
            return DEFAULT_CURRENCY;
        }
        return accounts.stream()
                .collect(java.util.stream.Collectors.groupingBy(Account::getCurrency,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_CURRENCY);
    }

    /** Guards the summary endpoint's optional custom dates. */
    static void requireCustomDates(PeriodType type, LocalDate start, LocalDate end) {
        if (type == PeriodType.CUSTOM && (start == null || end == null)) {
            throw new BadRequestException("A CUSTOM period requires both 'start' and 'end'");
        }
    }
}
