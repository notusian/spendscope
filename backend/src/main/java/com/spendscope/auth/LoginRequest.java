package com.spendscope.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.spendscope.common.validation.TrimmedString;
import com.spendscope.common.validation.Utf8Length;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonDeserialize(converter = TrimmedString.class)
        @NotBlank(message = "Email is required")
        @Size(max = 320)
        String email,

        @NotBlank(message = "Password is required")
        @Utf8Length(max = 72)
        String password) {
}
