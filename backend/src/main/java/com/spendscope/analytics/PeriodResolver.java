package com.spendscope.analytics;

import com.spendscope.common.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Turns a {@link PeriodType} into concrete dates, and works out what "the
 * previous period" means.
 *
 * <p>The comparison rule is the interesting part, because the obvious answer is
 * wrong. On 5 September, "this month" covers five days. Comparing it against
 * the whole of August would report a 90% drop in spending every month, on the
 * 5th of every month, forever.
 *
 * <p>So:
 * <ul>
 *   <li><b>Calendar periods</b> (this month, last month, this year) compare
 *       against <em>the same slice</em> of the preceding calendar period:
 *       1-5 September against 1-5 August.</li>
 *   <li><b>Rolling windows</b> (last 30/90 days, custom) compare against the
 *       equally long window immediately before them.</li>
 * </ul>
 *
 * <p>A {@link Clock} is injected rather than calling {@code LocalDate.now()}
 * so tests can pin "today" and assert on exact dates.
 */
@Component
public class PeriodResolver {

    private final Clock clock;

    public PeriodResolver(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * @param customStart required when {@code type} is {@link PeriodType#CUSTOM}
     * @param customEnd   required when {@code type} is {@link PeriodType#CUSTOM}
     */
    public DateRange resolve(PeriodType type, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = today();
        return switch (type) {
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case LAST_MONTH -> {
                LocalDate firstOfLastMonth = today.withDayOfMonth(1).minusMonths(1);
                yield new DateRange(firstOfLastMonth, firstOfLastMonth.withDayOfMonth(
                        firstOfLastMonth.lengthOfMonth()));
            }
            case LAST_30_DAYS -> new DateRange(today.minusDays(29), today);
            case LAST_90_DAYS -> new DateRange(today.minusDays(89), today);
            case THIS_YEAR -> new DateRange(today.withDayOfYear(1), today);
            case CUSTOM -> {
                if (customStart == null || customEnd == null) {
                    throw new BadRequestException("A CUSTOM period requires both start and end dates");
                }
                yield new DateRange(customStart, customEnd);
            }
        };
    }

    /**
     * @return the range this period should be compared against.
     */
    public DateRange previousOf(PeriodType type, DateRange current) {
        if (!type.isCalendarAligned()) {
            return current.immediatelyBefore();
        }
        return switch (type) {
            case THIS_MONTH, LAST_MONTH -> sameSliceOfPreviousMonth(current);
            case THIS_YEAR -> sameSliceOfPreviousYear(current);
            default -> current.immediatelyBefore();
        };
    }

    /**
     * 1-5 September becomes 1-5 August. A full month becomes the full previous
     * month, including the short-month case: 1-31 March compares against
     * 1-28 February, not against a non-existent 31 February.
     */
    private DateRange sameSliceOfPreviousMonth(DateRange current) {
        LocalDate previousMonthStart = current.start().minusMonths(1).withDayOfMonth(1);
        boolean completeMonth = current.end().getDayOfMonth() == current.end().lengthOfMonth();
        LocalDate previousMonthEnd = completeMonth
                ? previousMonthStart.withDayOfMonth(previousMonthStart.lengthOfMonth())
                : current.end().minusMonths(1);
        return new DateRange(previousMonthStart, previousMonthEnd);
    }

    /** 1 Jan - 5 Sep 2026 becomes 1 Jan - 5 Sep 2025. */
    private DateRange sameSliceOfPreviousYear(DateRange current) {
        LocalDate previousYearStart = current.start().minusYears(1).withDayOfYear(1);
        LocalDate previousYearEnd = current.end().minusYears(1);
        return new DateRange(previousYearStart, previousYearEnd);
    }
}
