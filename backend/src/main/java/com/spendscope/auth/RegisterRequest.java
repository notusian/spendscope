package com.spendscope.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.spendscope.common.validation.TrimmedString;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password 72 bytes is BCrypt's hard limit - anything beyond it is
 *                 silently ignored by the algorithm, so it is rejected here
 *                 rather than giving the user a false sense of strength.
 */
public record RegisterRequest(
        @JsonDeserialize(converter = TrimmedString.class)
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        String password) {
}
