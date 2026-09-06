package com.spendscope.recurring;

import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recurring")
@Tag(name = "Recurring expenses")
public class RecurringController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @GetMapping
    @Operation(summary = "Detect likely subscriptions and other regular payments")
    public List<RecurringExpenseResponse> detect(@AuthenticationPrincipal AppUserDetails principal) {
        return recurringExpenseService.detect(principal.getUserId());
    }
}
