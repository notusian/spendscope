package com.spendscope.common.error;

/**
 * Stable, machine readable error identifiers returned in every error response.
 *
 * <p>Clients branch on these rather than on human readable messages, which are
 * free to change.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    NOT_FOUND,
    CONFLICT,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    IMPORT_FAILED,
    RATE_LIMITED,
    INTERNAL_ERROR
}
