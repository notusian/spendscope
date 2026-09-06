package com.spendscope.budget;

import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@Validated
@Tag(name = "Budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "List budgets and their progress for a month")
    public List<BudgetProgressResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Month must be in YYYY-MM format")
            String month) {
        return budgetService.listForMonth(principal.getUserId(),
                month == null ? null : YearMonth.parse(month));
    }

    @PostMapping
    @Operation(summary = "Create a monthly budget for a category")
    public ResponseEntity<BudgetProgressResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                         @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.create(principal.getUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a budget's amount")
    public BudgetProgressResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                         @PathVariable Long id,
                                         @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        budgetService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
