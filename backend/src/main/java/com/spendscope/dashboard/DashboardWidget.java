package com.spendscope.dashboard;

import com.spendscope.analytics.AnalyticsQueryRequest;
import com.spendscope.analytics.GroupBy;
import com.spendscope.analytics.PeriodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One configurable card on a user's dashboard.
 *
 * <p>The knobs every widget shares are typed columns so the database can check
 * them. {@link #configuration} is JSONB for the few widget-specific extras
 * (how many merchants to list, which category a budget bar tracks) that would
 * otherwise mean a mostly-null column per widget type.
 */
@Entity
@Table(name = "dashboard_widgets")
public class DashboardWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 40)
    private WidgetType metricType;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_by", length = 20)
    private GroupBy groupBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodType period;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_period", length = 20)
    private AnalyticsQueryRequest.Comparison comparisonPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "chart_type", nullable = false, length = 20)
    private ChartType chartType;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WidgetSize size;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DashboardWidget() {
        // Required by JPA.
    }

    public DashboardWidget(Long userId,
                           WidgetType metricType,
                           GroupBy groupBy,
                           PeriodType period,
                           AnalyticsQueryRequest.Comparison comparisonPeriod,
                           ChartType chartType,
                           int position,
                           WidgetSize size,
                           Map<String, Object> configuration) {
        this.userId = userId;
        this.metricType = metricType;
        this.groupBy = groupBy;
        this.period = period;
        this.comparisonPeriod = comparisonPeriod;
        this.chartType = chartType;
        this.position = position;
        this.size = size;
        this.configuration = configuration == null ? Map.of() : configuration;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public WidgetType getMetricType() {
        return metricType;
    }

    public void setMetricType(WidgetType metricType) {
        this.metricType = metricType;
    }

    public GroupBy getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(GroupBy groupBy) {
        this.groupBy = groupBy;
    }

    public PeriodType getPeriod() {
        return period;
    }

    public void setPeriod(PeriodType period) {
        this.period = period;
    }

    public AnalyticsQueryRequest.Comparison getComparisonPeriod() {
        return comparisonPeriod;
    }

    public void setComparisonPeriod(AnalyticsQueryRequest.Comparison comparisonPeriod) {
        this.comparisonPeriod = comparisonPeriod;
    }

    public ChartType getChartType() {
        return chartType;
    }

    public void setChartType(ChartType chartType) {
        this.chartType = chartType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public WidgetSize getSize() {
        return size;
    }

    public void setSize(WidgetSize size) {
        this.size = size;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration == null ? Map.of() : configuration;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DashboardWidget widget) || id == null) {
            return false;
        }
        return id.equals(widget.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
