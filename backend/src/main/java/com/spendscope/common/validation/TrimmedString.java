package com.spendscope.common.validation;

import com.fasterxml.jackson.databind.util.StdConverter;

/**
 * Trims surrounding whitespace off a JSON string during deserialisation.
 *
 * <p>Applied to the email fields. Bean Validation's {@code @Email} rejects
 * " alice@example.com " outright, so a user who typed a trailing space - or
 * whose password manager added one - would be told their address is invalid
 * rather than simply having it accepted. Trimming happens before validation,
 * so the rule stays strict about what an email is while being forgiving about
 * whitespace.
 */
public class TrimmedString extends StdConverter<String, String> {

    @Override
    public String convert(String value) {
        return value == null ? null : value.trim();
    }
}
