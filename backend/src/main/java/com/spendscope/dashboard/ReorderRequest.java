package com.spendscope.dashboard;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderRequest(
        @NotEmpty(message = "Widget ids are required")
        List<Long> widgetIds) {
}
