package com.spendscope.dashboard;

import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard/widgets")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "List the caller's dashboard widgets in display order")
    public List<WidgetResponse> list(@AuthenticationPrincipal AppUserDetails principal) {
        return dashboardService.list(principal.getUserId());
    }

    @PostMapping
    @Operation(summary = "Add a widget")
    public ResponseEntity<WidgetResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                 @Valid @RequestBody WidgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dashboardService.create(principal.getUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reconfigure a widget")
    public WidgetResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody WidgetRequest request) {
        return dashboardService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a widget")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        dashboardService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    @Operation(summary = "Reorder the whole dashboard")
    public List<WidgetResponse> reorder(@AuthenticationPrincipal AppUserDetails principal,
                                        @Valid @RequestBody ReorderRequest request) {
        return dashboardService.reorder(principal.getUserId(), request.widgetIds());
    }

    @PostMapping("/reset")
    @Operation(summary = "Restore the default dashboard layout")
    public List<WidgetResponse> reset(@AuthenticationPrincipal AppUserDetails principal) {
        return dashboardService.resetToDefaults(principal.getUserId());
    }
}
