package com.spendscope.auth;

import com.spendscope.common.validation.Utf8Length;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Utf8Length(max = 72)
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 10, message = "Password must contain at least 10 characters")
        @Utf8Length(max = 72)
        String newPassword) {
}
