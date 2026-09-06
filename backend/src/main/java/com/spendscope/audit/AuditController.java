package com.spendscope.audit;

import com.spendscope.common.web.PageResponse;
import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user's own security activity.
 *
 * <p>Scoped to the caller: this is "your recent logins and changes", not an
 * administrative view of the system log.
 */
@RestController
@RequestMapping("/api/security/events")
@Validated
@Tag(name = "Security activity")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List the caller's recent account activity")
    public PageResponse<AuditEventResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return auditService.listForUser(principal.getUserId(), page, size);
    }
}
