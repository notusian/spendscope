package com.spendscope.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts that a string is a real ISO-4217 currency code such as {@code SGD}.
 *
 * <p>A regex of {@code [A-Z]{3}} would accept "ZZZ"; this checks against the
 * JDK's currency table instead.
 */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrencyCode {

    String message() default "Currency must be a valid ISO-4217 code such as SGD";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** When true, {@code null} is accepted (used for optional overrides). */
    boolean optional() default false;
}
