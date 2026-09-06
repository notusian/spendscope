package com.spendscope.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a request collides with existing state (duplicate email, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
