package com.spendscope.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(max = 72)
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        String newPassword) {
}
