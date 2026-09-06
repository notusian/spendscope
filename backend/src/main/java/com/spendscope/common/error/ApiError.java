package com.spendscope.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every failing endpoint returns.
 *
 * <p>Deliberately free of stack traces, SQL fragments and exception class
 * names: those go to the server log, never to the client.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-05T12:00:00Z",
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "The request contains invalid fields",
 *   "path": "/api/transactions",
 *   "fieldErrors": { "amount": "Amount is required" }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError withFieldErrors(int status,
                                           ErrorCode code,
                                           String message,
                                           String path,
                                           Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, path, fieldErrors);
    }
}
