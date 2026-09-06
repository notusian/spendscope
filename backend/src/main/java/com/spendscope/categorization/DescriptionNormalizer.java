package com.spendscope.categorization;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a raw bank statement description into a stable, comparable string.
 *
 * <p>Bank descriptions for the same merchant vary constantly:
 * <pre>
 *   "STARBUCKS #4821 SINGAPORE SG"
 *   "starbucks  coffee  4821"
 *   "POS DEBIT STARBUCKS 12/08"
 * </pre>
 * All three normalise to {@code STARBUCKS COFFEE} style text that merchant
 * matching and duplicate fingerprinting can rely on.
 *
 * <p>The transformation is deliberately deterministic and side-effect free so
 * it can be unit tested exhaustively, and so a fingerprint computed today
 * still matches one computed last year.
 */
@Component
public class DescriptionNormalizer {

    /**
     * Card processing noise and legal/location suffixes that carry no merchant
     * information.
     *
     * <p>Note what is deliberately <em>absent</em>: "SINGAPORE". Country
     * abbreviations used as suffixes ("SG", "SGP") are noise, but the full word
     * is part of real merchant names - Singapore Airlines, Singapore Pools -
     * and stripping it would erase the merchant instead of the noise.
     */
    private static final Pattern NOISE_TOKENS = Pattern.compile(
            "\\b(POS|PURCHASE|PAYMENT|DEBIT|CREDIT|CARD|TRANSACTION|TXN|REF|VISA|MASTERCARD|NETS|PAYNOW|"
                    + "GIRO|IBANKING|TRANSFER|WWW|COM|SG|SGP|PTE|LTD|LLC|INC)\\b");

    /** Long digit runs are reference/terminal numbers, not merchant names. */
    private static final Pattern LONG_DIGIT_RUNS = Pattern.compile("\\b\\d{3,}\\b");

    /** Dates embedded in the description, e.g. 12/08 or 2026-08-01. */
    private static final Pattern EMBEDDED_DATES = Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}([/-]\\d{1,4})?\\b");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITIC_MARKS = Pattern.compile("\\p{M}");

    /** Matches the transactions.normalized_description column width. */
    private static final int MAX_LENGTH = 500;

    /**
     * @param rawDescription the description exactly as the user or the CSV
     *                       supplied it
     * @return an upper-cased, punctuation-free, noise-free version; never null,
     *         and never longer than the column allows
     */
    public String normalise(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "";
        }

        // Decompose accented characters ("Café" -> "Cafe") so that the same
        // merchant typed with and without accents compares equal.
        String text = Normalizer.normalize(rawDescription, Normalizer.Form.NFD);
        text = DIACRITIC_MARKS.matcher(text).replaceAll("");

        text = text.toUpperCase(Locale.ROOT);
        text = EMBEDDED_DATES.matcher(text).replaceAll(" ");
        text = NON_ALPHANUMERIC.matcher(text).replaceAll(" ");
        text = LONG_DIGIT_RUNS.matcher(text).replaceAll(" ");
        text = NOISE_TOKENS.matcher(text).replaceAll(" ");
        text = REPEATED_WHITESPACE.matcher(text).replaceAll(" ").trim();

        // Stripping noise can empty the string entirely (a description that was
        // only a reference number). Fall back to a simple upper-case squeeze so
        // downstream code always has something to fingerprint.
        if (text.isEmpty()) {
            text = REPEATED_WHITESPACE.matcher(rawDescription.toUpperCase(Locale.ROOT)).replaceAll(" ").trim();
        }

        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }
}
