package com.spendscope.analytics;

import com.spendscope.common.error.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Period resolution with "today" pinned to a fixed date.
 *
 * A fixed clock is what makes these assertions possible at all: without it,
 * "this month" would mean something different every time the suite ran.
 */
class PeriodResolverTest {

    /** A Thursday in the middle of a 30-day month, in a non-leap year. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    private final PeriodResolver resolver =
            new PeriodResolver(Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("this month runs from the 1st to today, not to the end of the month")
        void thisMonth() {
            DateRange range = resolver.resolve(PeriodType.THIS_MONTH, null, null);
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(range.end()).isEqualTo(TODAY);
            assertThat(range.lengthInDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("last month is the whole previous calendar month")
        void lastMonth() {
            DateRange range = resolver.resolve(PeriodType.LAST_MONTH, null, null);
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("last 30 days includes today, so it is 30 days and not 31")
        void last30Days() {
            DateRange range = resolver.resolve(PeriodType.LAST_30_DAYS, null, null);
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 8, 7));
            assertThat(range.end()).isEqualTo(TODAY);
            assertThat(range.lengthInDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("last 90 days is 90 days inclusive")
        void last90Days() {
            assertThat(resolver.resolve(PeriodType.LAST_90_DAYS, null, null).lengthInDays()).isEqualTo(90);
        }

        @Test
        @DisplayName("this year runs from 1 January to today")
        void thisYear() {
            DateRange range = resolver.resolve(PeriodType.THIS_YEAR, null, null);
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(range.end()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("a custom period uses the supplied dates")
        void customPeriod() {
            DateRange range = resolver.resolve(
                    PeriodType.CUSTOM, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("a custom period without dates is rejected")
        void customPeriodRequiresDates() {
            assertThatThrownBy(() -> resolver.resolve(PeriodType.CUSTOM, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("requires both start and end");
        }

        @Test
        @DisplayName("an inverted custom range is rejected rather than silently returning nothing")
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> resolver.resolve(
                    PeriodType.CUSTOM, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be after");
        }
    }

    @Nested
    @DisplayName("previousOf")
    class PreviousOf {

        @Test
        @DisplayName("a partial month compares against the same slice of the previous month")
        void partialMonthComparesLikeForLike() {
            // This is the whole point of the rule. Comparing 1-5 September
            // against the whole of August would report a ~90% drop on the 5th of
            // every month, forever.
            DateRange current = resolver.resolve(PeriodType.THIS_MONTH, null, null);
            DateRange previous = resolver.previousOf(PeriodType.THIS_MONTH, current);

            assertThat(previous.start()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(previous.end()).isEqualTo(LocalDate.of(2026, 8, 5));
            assertThat(previous.lengthInDays()).isEqualTo(current.lengthInDays());
        }

        @Test
        @DisplayName("a full month compares against the full previous month")
        void fullMonthComparesToFullMonth() {
            DateRange august = resolver.resolve(PeriodType.LAST_MONTH, null, null);
            DateRange july = resolver.previousOf(PeriodType.LAST_MONTH, august);

            assertThat(july.start()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(july.end()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("a 31 day month compares against a shorter month without inventing days")
        void handlesShortPreviousMonth() {
            // 1-31 March has no 1-31 February to compare against. The previous
            // range must stop at the 28th rather than overflow into March.
            DateRange march = new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
            DateRange february = resolver.previousOf(PeriodType.LAST_MONTH, march);

            assertThat(february.start()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(february.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void completedFebruaryIncludesAllOfJanuary() {
            DateRange february = new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
            DateRange january = resolver.previousOf(PeriodType.LAST_MONTH, february);
            assertThat(january.start()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(january.end()).isEqualTo(LocalDate.of(2026, 1, 31));
        }

        @Test
        void partialLongMonthClampsToThePreviousMonthsEnd() {
            DateRange march = new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));
            assertThat(resolver.previousOf(PeriodType.THIS_MONTH, march).end())
                    .isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("a rolling window compares against the window immediately before it")
        void rollingWindowShiftsBack() {
            DateRange current = resolver.resolve(PeriodType.LAST_30_DAYS, null, null);
            DateRange previous = resolver.previousOf(PeriodType.LAST_30_DAYS, current);

            assertThat(previous.end()).isEqualTo(current.start().minusDays(1));
            assertThat(previous.lengthInDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("this year compares against the same slice of last year")
        void thisYearComparesToSameSliceLastYear() {
            DateRange current = resolver.resolve(PeriodType.THIS_YEAR, null, null);
            DateRange previous = resolver.previousOf(PeriodType.THIS_YEAR, current);

            assertThat(previous.start()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(previous.end()).isEqualTo(LocalDate.of(2025, 9, 5));
        }

        @Test
        void yearComparisonPreservesCalendarDatesAcrossLeapYears() {
            for (int year : new int[] {2024, 2025}) {
                DateRange current = new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 1));
                assertThat(resolver.previousOf(PeriodType.THIS_YEAR, current).end())
                        .isEqualTo(LocalDate.of(year - 1, 3, 1));
            }
            DateRange leapDay = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29));
            assertThat(resolver.previousOf(PeriodType.THIS_YEAR, leapDay).end())
                    .isEqualTo(LocalDate.of(2023, 2, 28));
        }

        @Test
        @DisplayName("a custom period compares against an equally long window before it")
        void customComparesToPrecedingWindow() {
            DateRange current = new DateRange(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 19));
            DateRange previous = resolver.previousOf(PeriodType.CUSTOM, current);

            assertThat(previous.start()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(previous.end()).isEqualTo(LocalDate.of(2026, 6, 9));
            assertThat(previous.lengthInDays()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("DateRange")
    class Ranges {

        @Test
        @DisplayName("a single day range has a length of one")
        void singleDayIsOneDay() {
            LocalDate day = LocalDate.of(2026, 9, 5);
            assertThat(new DateRange(day, day).lengthInDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects an absurdly long range")
        void rejectsExcessiveRange() {
            assertThatThrownBy(() -> new DateRange(LocalDate.of(1900, 1, 1), LocalDate.of(2026, 1, 1)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be longer than");
        }
    }
}
