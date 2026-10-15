package com.spendscope.importjob;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

/**
 * Lenient-but-explicit parsing of the two awkward CSV columns.
 *
 * <p>Bank exports are not consistent, but "lenient" must not mean "guesses".
 * Every accepted format is listed here, and anything else is a rejected row
 * with a message the user can act on.
 */
final class CsvValueParsers {

    /**
     * Accepted date formats, tried in order.
     *
     * <p>{@code dd/MM/yyyy} is supported because most non-US bank exports use
     * it. {@code MM/dd/yyyy} is deliberately <em>not</em> supported: it is
     * indistinguishable from {@code dd/MM/yyyy} for the first twelve days of
     * every month, and silently importing 3 April as 4 March would corrupt a
     * user's history in a way they would never notice.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                          // 2026-08-01
            strictDateFormat("dd/MM/uuuu"),                           // 01/08/2026
            strictDateFormat("dd-MM-uuuu"),                           // 01-08-2026
            strictDateFormat("uuuu/MM/dd"));                          // 2026/08/01

    private static final BigDecimal SMALLEST_ROUNDABLE_AMOUNT = new BigDecimal("0.00005");

    /** Currency symbols and grouping separators that may surround an amount. */
    private static final String AMOUNT_NOISE = "[\\s\\u00A0$€£¥₹,'](?!$)";

    private CsvValueParsers() {
    }

    private static DateTimeFormatter strictDateFormat(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    static LocalDate parseDate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new CsvRowException("Date is required");
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        throw new CsvRowException("Date '" + value + "' is not a supported format (use YYYY-MM-DD)");
    }

    /**
     * Parses an amount, accepting the shapes bank exports actually produce.
     *
     * <pre>
     *   -7.20      -> -7.20
     *   (7.20)     -> -7.20   accounting notation for a negative
     *   $1,234.56  ->  1234.56
     *   +50        ->  50
     * </pre>
     */
    static BigDecimal parseAmount(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new CsvRowException("Amount is required");
        }

        boolean parenthesised = value.startsWith("(") && value.endsWith(")");
        if (parenthesised) {
            value = value.substring(1, value.length() - 1).trim();
        }

        value = value.replaceAll(AMOUNT_NOISE, "");
        if (value.startsWith("+")) {
            value = value.substring(1);
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new CsvRowException("Amount '" + raw.trim() + "' is not a number");
        }

        if (parenthesised) {
            amount = amount.abs().negate();
        }
        // Reject values that would round to zero before scaling, including
        // scientific notation with an extremely small exponent.
        if (amount.abs().compareTo(SMALLEST_ROUNDABLE_AMOUNT) < 0) {
            throw new CsvRowException("Amount must not be zero");
        }
        // Matches the NUMERIC(19,4) column: 15 integer digits and 4 decimals.
        if ((long) amount.precision() - amount.scale() > 15) {
            throw new CsvRowException("Amount is too large");
        }
        amount = amount.setScale(4, java.math.RoundingMode.HALF_UP);
        // Rounding can carry into a sixteenth integer digit.
        if (amount.precision() - amount.scale() > 15) {
            throw new CsvRowException("Amount is too large");
        }
        return amount;
    }
}
