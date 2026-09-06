package com.spendscope.common.validation;

import com.spendscope.common.Money;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private boolean optional;

    @Override
    public void initialize(CurrencyCode annotation) {
        this.optional = annotation.optional();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        return Money.isValidCurrencyCode(value);
    }
}
