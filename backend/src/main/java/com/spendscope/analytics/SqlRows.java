package com.spendscope.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Converters for the {@code Object[]} rows returned by the native aggregate
 * queries.
 *
 * <p>JDBC drivers are free to hand back {@code Integer} where a {@code Long} is
 * expected, or {@code java.sql.Date} where a {@code LocalDate} is. Funnelling
 * every read through these three methods keeps that defensiveness in one place
 * instead of scattered casts.
 */
final class SqlRows {

    private SqlRows() {
    }

    static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    static long asPrimitiveLong(Object value) {
        Long converted = asLong(value);
        return converted == null ? 0L : converted;
    }

    static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    static LocalDate asLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
