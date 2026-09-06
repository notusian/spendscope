package com.spendscope.analytics;

import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Two endpoints, on purpose.
 *
 * <p>{@code /summary} answers the one question every dashboard opens with, in a
 * single round trip. {@code /query} covers everything else through the
 * generalised request, so a new widget type needs no new endpoint.
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Headline spending metrics for a period, with previous-period comparison")
    public AnalyticsSummaryResponse summary(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "THIS_MONTH") PeriodType period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        AnalyticsService.requireCustomDates(period, start, end);
        return analyticsService.summary(principal.getUserId(), period, start, end);
    }

    @PostMapping("/query")
    @Operation(summary = "Run a metric/period/grouping/comparison query")
    public AnalyticsResponse query(@AuthenticationPrincipal AppUserDetails principal,
                                   @Valid @RequestBody AnalyticsQueryRequest request) {
        return analyticsService.query(principal.getUserId(), request);
    }
}
