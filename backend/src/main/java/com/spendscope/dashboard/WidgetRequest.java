package com.spendscope.dashboard;

import com.spendscope.analytics.AnalyticsQueryRequest;
import com.spendscope.analytics.GroupBy;
import com.spendscope.analytics.PeriodType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WidgetRequest(
        @NotNull(message = "Widget type is required")
        WidgetType metricType,

        GroupBy groupBy,

        @NotNull(message = "Period is required")
        PeriodType period,

        AnalyticsQueryRequest.Comparison comparisonPeriod,

        @NotNull(message = "Chart type is required")
        ChartType chartType,

        @Min(value = 0, message = "Position must be zero or greater")
        Integer position,

        @NotNull(message = "Size is required")
        WidgetSize size,

        /**
         * Free-form widget options. Whitelisted and size-capped by
         * {@code DashboardService.sanitiseConfiguration} before it is stored,
         * because this map goes into the database as-is.
         */
        Map<String, Object> configuration) {
}
