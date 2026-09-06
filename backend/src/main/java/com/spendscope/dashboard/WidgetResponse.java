package com.spendscope.dashboard;

import com.spendscope.analytics.AnalyticsQueryRequest;
import com.spendscope.analytics.GroupBy;
import com.spendscope.analytics.Metric;
import com.spendscope.analytics.PeriodType;

import java.util.Map;

/**
 * @param metric the analytics metric this widget runs, or null for widgets
 *               backed by their own endpoint (budgets, recurring, anomalies)
 */
public record WidgetResponse(
        Long id,
        WidgetType metricType,
        Metric metric,
        GroupBy groupBy,
        PeriodType period,
        AnalyticsQueryRequest.Comparison comparisonPeriod,
        ChartType chartType,
        int position,
        WidgetSize size,
        Map<String, Object> configuration) {

    public static WidgetResponse from(DashboardWidget widget) {
        return new WidgetResponse(
                widget.getId(),
                widget.getMetricType(),
                widget.getMetricType().metric(),
                widget.getGroupBy(),
                widget.getPeriod(),
                widget.getComparisonPeriod(),
                widget.getChartType(),
                widget.getPosition(),
                widget.getSize(),
                widget.getConfiguration());
    }
}
