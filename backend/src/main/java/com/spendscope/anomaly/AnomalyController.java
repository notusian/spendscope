package com.spendscope.anomaly;

import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insights/anomalies")
@Tag(name = "Insights")
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @GetMapping
    @Operation(summary = "Categories whose spending this month is unusually high for this user")
    public List<SpendingAnomaly> detect(@AuthenticationPrincipal AppUserDetails principal) {
        return anomalyService.detect(principal.getUserId());
    }
}
