package com.spendscope.dashboard;

import com.spendscope.analytics.AnalyticsQueryRequest;
import com.spendscope.analytics.GroupBy;
import com.spendscope.analytics.PeriodType;
import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.common.error.BadRequestException;
import com.spendscope.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persisted dashboard layout.
 *
 * <p>A widget is a saved analytics request plus presentation. Because the
 * analytics layer is generic, adding a widget needs no backend change - which
 * is the whole reason the dashboard is configurable at all.
 */
@Service
public class DashboardService {

    /**
     * Keys accepted inside a widget's JSONB configuration. An allowlist rather
     * than a denylist: the map is written to the database and read back into
     * the UI, so unbounded keys would be an easy place to stash junk.
     */
    private static final Set<String> ALLOWED_CONFIGURATION_KEYS =
            Set.of("limit", "title", "categoryId", "showLegend", "month");

    private static final int MAX_CONFIGURATION_ENTRIES = 10;
    private static final int MAX_CONFIGURATION_VALUE_LENGTH = 120;
    private static final int MAX_WIDGETS_PER_USER = 24;

    private final DashboardWidgetRepository widgetRepository;
    private final AuditService auditService;

    public DashboardService(DashboardWidgetRepository widgetRepository, AuditService auditService) {
        this.widgetRepository = widgetRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<WidgetResponse> list(Long userId) {
        return widgetRepository.findByUserIdOrderByPositionAscIdAsc(userId).stream()
                .map(WidgetResponse::from)
                .toList();
    }

    @Transactional
    public WidgetResponse create(Long userId, WidgetRequest request) {
        List<DashboardWidget> existing = widgetRepository.findByUserIdOrderByPositionAscIdAsc(userId);
        if (existing.size() >= MAX_WIDGETS_PER_USER) {
            throw new BadRequestException("A dashboard can hold at most " + MAX_WIDGETS_PER_USER + " widgets");
        }
        validate(request);

        int position = request.position() != null ? request.position() : existing.size();
        DashboardWidget widget = new DashboardWidget(
                userId,
                request.metricType(),
                request.groupBy(),
                request.period(),
                request.comparisonPeriod(),
                request.chartType(),
                position,
                request.size(),
                sanitiseConfiguration(request.configuration()));

        WidgetResponse saved = WidgetResponse.from(widgetRepository.save(widget));
        auditService.record(userId, AuditEventType.DASHBOARD_UPDATED, Map.of("action", "widget_created"));
        return saved;
    }

    @Transactional
    public WidgetResponse update(Long userId, Long widgetId, WidgetRequest request) {
        validate(request);
        DashboardWidget widget = require(userId, widgetId);
        widget.setMetricType(request.metricType());
        widget.setGroupBy(request.groupBy());
        widget.setPeriod(request.period());
        widget.setComparisonPeriod(request.comparisonPeriod());
        widget.setChartType(request.chartType());
        widget.setSize(request.size());
        if (request.position() != null) {
            widget.setPosition(request.position());
        }
        widget.setConfiguration(sanitiseConfiguration(request.configuration()));
        auditService.record(userId, AuditEventType.DASHBOARD_UPDATED, Map.of("action", "widget_updated"));
        return WidgetResponse.from(widget);
    }

    @Transactional
    public void delete(Long userId, Long widgetId) {
        widgetRepository.delete(require(userId, widgetId));
        auditService.record(userId, AuditEventType.DASHBOARD_UPDATED, Map.of("action", "widget_deleted"));
    }

    /**
     * Applies a new ordering.
     *
     * <p>Every id is checked against the caller before anything is written, so
     * a list containing someone else's widget id fails the whole request
     * instead of partially reordering.
     */
    @Transactional
    public List<WidgetResponse> reorder(Long userId, List<Long> widgetIdsInOrder) {
        List<DashboardWidget> widgets = widgetRepository.findByUserIdOrderByPositionAscIdAsc(userId);
        Set<Long> ownedIds = widgets.stream().map(DashboardWidget::getId).collect(java.util.stream.Collectors.toSet());

        if (widgetIdsInOrder.size() != ownedIds.size() || !ownedIds.equals(new HashSet<>(widgetIdsInOrder))) {
            throw new BadRequestException("The ordering must list each of your widgets exactly once");
        }

        Map<Long, DashboardWidget> byId = new LinkedHashMap<>();
        widgets.forEach(widget -> byId.put(widget.getId(), widget));
        for (int position = 0; position < widgetIdsInOrder.size(); position++) {
            byId.get(widgetIdsInOrder.get(position)).setPosition(position);
        }

        auditService.record(userId, AuditEventType.DASHBOARD_UPDATED, Map.of("action", "reordered"));
        return list(userId);
    }

    @Transactional
    public List<WidgetResponse> resetToDefaults(Long userId) {
        widgetRepository.deleteByUserId(userId);
        createDefaultWidgets(userId);
        return list(userId);
    }

    /**
     * The starter dashboard every new user gets.
     *
     * <p>An empty dashboard on first login looks broken; these eight widgets
     * demonstrate what the product does as soon as any data exists.
     */
    @Transactional
    public void createDefaultWidgets(Long userId) {
        if (widgetRepository.existsByUserId(userId)) {
            return;
        }
        List<DashboardWidget> defaults = List.of(
                widget(userId, WidgetType.TOTAL_SPENDING, null, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD, ChartType.NUMBER, 0, WidgetSize.SMALL,
                        Map.of("title", "Total Spending")),
                widget(userId, WidgetType.TOTAL_SPENDING, null, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD, ChartType.CHANGE, 1, WidgetSize.SMALL,
                        Map.of("title", "Monthly Change")),
                widget(userId, WidgetType.AVERAGE_DAILY_SPENDING, null, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD, ChartType.NUMBER, 2, WidgetSize.SMALL,
                        Map.of("title", "Average Daily")),
                widget(userId, WidgetType.TRANSACTION_COUNT, null, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD, ChartType.NUMBER, 3, WidgetSize.SMALL,
                        Map.of("title", "Transactions")),
                widget(userId, WidgetType.SPENDING_TREND, GroupBy.DAY, PeriodType.LAST_30_DAYS,
                        AnalyticsQueryRequest.Comparison.NONE, ChartType.LINE, 4, WidgetSize.FULL,
                        Map.of("title", "Spending Over Time")),
                widget(userId, WidgetType.CATEGORY_SPENDING, GroupBy.CATEGORY, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD, ChartType.DONUT, 5, WidgetSize.MEDIUM,
                        Map.of("title", "Categories", "limit", 8)),
                widget(userId, WidgetType.TOP_MERCHANTS, GroupBy.MERCHANT, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.NONE, ChartType.LIST, 6, WidgetSize.MEDIUM,
                        Map.of("title", "Top Merchants", "limit", 6)),
                widget(userId, WidgetType.BUDGET_PROGRESS, null, PeriodType.THIS_MONTH,
                        AnalyticsQueryRequest.Comparison.NONE, ChartType.PROGRESS, 7, WidgetSize.MEDIUM,
                        Map.of("title", "Budget Progress")),
                widget(userId, WidgetType.RECURRING_EXPENSES, null, PeriodType.LAST_90_DAYS,
                        AnalyticsQueryRequest.Comparison.NONE, ChartType.LIST, 8, WidgetSize.MEDIUM,
                        Map.of("title", "Recurring Expenses")));
        widgetRepository.saveAll(defaults);
    }

    private DashboardWidget require(Long userId, Long widgetId) {
        return widgetRepository.findByIdAndUserId(widgetId, userId)
                .orElseThrow(() -> NotFoundException.of("Widget", widgetId));
    }

    private DashboardWidget widget(Long userId,
                                   WidgetType type,
                                   GroupBy groupBy,
                                   PeriodType period,
                                   AnalyticsQueryRequest.Comparison comparison,
                                   ChartType chartType,
                                   int position,
                                   WidgetSize size,
                                   Map<String, Object> configuration) {
        return new DashboardWidget(userId, type, groupBy, period, comparison, chartType, position, size,
                configuration);
    }

    /** Rejects combinations that would render as an empty or nonsensical card. */
    private void validate(WidgetRequest request) {
        WidgetType type = request.metricType();

        if (type.isAnalyticsBacked() && type.metric().impliedGroupBy() == null
                && request.groupBy() == null
                && requiresGrouping(request.chartType())) {
            throw new BadRequestException(
                    request.chartType() + " charts need a groupBy, for example DAY or CATEGORY");
        }
        if (request.chartType() == ChartType.CHANGE
                && request.comparisonPeriod() != AnalyticsQueryRequest.Comparison.PREVIOUS_PERIOD) {
            throw new BadRequestException("A CHANGE widget requires comparisonPeriod=PREVIOUS_PERIOD");
        }
        if (type == WidgetType.BUDGET_PROGRESS && request.chartType() != ChartType.PROGRESS) {
            throw new BadRequestException("Budget widgets must use the PROGRESS chart type");
        }
    }

    private boolean requiresGrouping(ChartType chartType) {
        return switch (chartType) {
            case LINE, BAR, PIE, DONUT, LIST -> true;
            case NUMBER, CHANGE, PROGRESS -> false;
        };
    }

    /**
     * Keeps the JSONB blob small and predictable: known keys only, scalar
     * values only, short strings only.
     */
    static Map<String, Object> sanitiseConfiguration(Map<String, Object> configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return Map.of();
        }
        if (configuration.size() > MAX_CONFIGURATION_ENTRIES) {
            throw new BadRequestException(
                    "Widget configuration must have at most " + MAX_CONFIGURATION_ENTRIES + " entries");
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        configuration.forEach((key, value) -> {
            if (!ALLOWED_CONFIGURATION_KEYS.contains(key) || value == null) {
                return;
            }
            if (value instanceof String text) {
                if (text.length() > MAX_CONFIGURATION_VALUE_LENGTH) {
                    throw new BadRequestException("Widget configuration values must be short strings");
                }
                safe.put(key, text);
            } else if (value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
            }
            // Nested objects and arrays are dropped: nothing needs them, and
            // they are the part that would grow without bound.
        });
        return safe;
    }
}
