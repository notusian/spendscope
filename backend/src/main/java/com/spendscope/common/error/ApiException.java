package com.spendscope.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for exceptions that map directly onto an HTTP response.
 *
 * <p>Anything thrown that is <em>not</em> an {@code ApiException} is treated as
 * a bug and reported to the client as a generic 500 with no internal detail.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    protected ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
