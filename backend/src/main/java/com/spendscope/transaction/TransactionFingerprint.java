package com.spendscope.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic identity for "the same transaction".
 *
 * <p>Re-uploading last month's statement on top of this month's should not
 * double every overlapping row. Statements have no stable transaction id, so
 * identity is derived from the fields that do not change between exports:
 *
 * <pre>
 *   userId | accountId | date | normalisedDescription | amount | currency
 * </pre>
 *
 * <p>Notes on the choices:
 * <ul>
 *   <li>{@code userId} and {@code accountId} are included so two users - or the
 *       same purchase on two different cards - never collide.</li>
 *   <li>The <em>normalised</em> description is used, so cosmetic differences
 *       between two exports of the same row do not produce different
 *       fingerprints.</li>
 *   <li>The amount is scaled before hashing, so {@code 7.2} and {@code 7.2000}
 *       hash identically.</li>
 *   <li>SHA-256 is used for a well-distributed fixed-width value, not for
 *       secrecy. Nothing security-sensitive depends on it.</li>
 * </ul>
 *
 * <p>A fingerprint collision means "probably a duplicate", never "definitely".
 * That is why the column is indexed but not unique: buying the same coffee
 * twice in one day is legitimate, and the import reports duplicates rather
 * than silently discarding them.
 */
public final class TransactionFingerprint {

    private static final String FIELD_SEPARATOR = "|";
    private static final int AMOUNT_SCALE = 4;

    private TransactionFingerprint() {
    }

    public static String compute(Long userId,
                                 Long accountId,
                                 LocalDate date,
                                 String normalisedDescription,
                                 BigDecimal amount,
                                 String currency) {
        String payload = String.join(FIELD_SEPARATOR,
                String.valueOf(userId),
                String.valueOf(accountId),
                String.valueOf(date),
                normalisedDescription == null ? "" : normalisedDescription,
                amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP).toPlainString(),
                currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        return sha256Hex(payload);
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JDK specification; this cannot happen.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
