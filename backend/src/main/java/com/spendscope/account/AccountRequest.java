package com.spendscope.account;

import com.spendscope.common.validation.CurrencyCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload.
 *
 * <p>The client cannot send {@code id}, {@code userId} or {@code createdAt}:
 * they are not fields on this record, which is what makes mass assignment
 * impossible. The entity is only ever populated from these named fields.
 */
public record AccountRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @NotNull(message = "Type is required")
        AccountType type,

        @CurrencyCode
        String currency) {
}
