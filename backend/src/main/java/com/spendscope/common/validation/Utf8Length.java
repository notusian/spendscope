package com.spendscope.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limits encoded bytes for fields whose storage or algorithm limit is not a character count. */
@Documented
@Constraint(validatedBy = Utf8LengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8Length {
    String message() default "Password must use at most {max} UTF-8 bytes";

    int max();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
