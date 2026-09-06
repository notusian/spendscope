package com.spendscope.analytics;

import com.spendscope.common.error.BadRequestException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive date window.
 *
 * <p>Both ends are inclusive, which matches how people talk about periods
 * ("1st to the 31st") and how the SQL {@code BETWEEN} clauses are written.
 */
public record DateRange(LocalDate start, LocalDate end) {

    /** A window longer than this is almost certainly a mistake and is expensive to aggregate. */
    public static final int MAX_DAYS = 366 * 10;

    public DateRange {
        if (start == null || end == null) {
            throw new BadRequestException("Both start and end dates are required");
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("Period start must not be after period end");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BadRequestException("Period must not be longer than " + MAX_DAYS + " days");
        }
    }

    /** Inclusive length, so a single-day range has length 1 and never 0. */
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** The window of equal length immediately before this one. */
    public DateRange immediatelyBefore() {
        long length = lengthInDays();
        return new DateRange(start.minusDays(length), start.minusDays(1));
    }
}
