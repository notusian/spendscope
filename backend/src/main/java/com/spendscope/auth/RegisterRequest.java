package com.spendscope.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.spendscope.common.validation.TrimmedString;
import com.spendscope.common.validation.Utf8Length;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password BCrypt accepts at most 72 UTF-8 bytes. Unicode characters
 *                 can occupy several bytes, so a character limit is insufficient.
 */
public record RegisterRequest(
        @JsonDeserialize(converter = TrimmedString.class)
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 10, message = "Password must contain at least 10 characters")
        @Utf8Length(max = 72)
        String password) {
}
