package com.spendscope.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource does not exist <em>or</em> is not owned by the caller.
 *
 * <p>Both cases deliberately produce the same 404. Returning 403 for
 * "exists but belongs to someone else" would leak which ids are in use.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " was not found");
    }
}
