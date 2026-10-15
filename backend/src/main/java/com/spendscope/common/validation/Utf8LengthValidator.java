package com.spendscope.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8LengthValidator implements ConstraintValidator<Utf8Length, String> {
    private int max;

    @Override
    public void initialize(Utf8Length annotation) {
        max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required fields use @NotBlank separately. The character check also
        // avoids allocating an encoded copy of an already oversized input.
        return value == null || (value.length() <= max
                && value.getBytes(StandardCharsets.UTF_8).length <= max);
    }
}
