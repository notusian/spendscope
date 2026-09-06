package com.spendscope.user;

import java.time.Instant;

/**
 * The only user shape the API ever returns.
 *
 * <p>There is intentionally no field for the password hash, the session id or
 * any other credential material - a DTO is what makes that guarantee
 * structural rather than a review comment.
 */
public record UserResponse(Long id, String email, boolean demo, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.isDemo(), user.getCreatedAt());
    }
}
