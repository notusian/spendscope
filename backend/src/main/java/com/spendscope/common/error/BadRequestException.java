package com.spendscope.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown for semantically invalid requests that annotation based validation
 * cannot express, e.g. "from" being after "to".
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }
}
