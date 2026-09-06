package com.spendscope.transaction;

import com.spendscope.common.web.PageResponse;
import com.spendscope.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "List transactions with filtering, sorting and pagination")
    public PageResponse<TransactionResponse> list(@AuthenticationPrincipal AppUserDetails principal,
                                                  @ParameterObject @Valid @ModelAttribute TransactionQuery query) {
        return transactionService.list(principal.getUserId(), query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one transaction owned by the caller")
    public TransactionResponse get(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        return transactionService.get(principal.getUserId(), id);
    }

    @PostMapping
    @Operation(summary = "Create a transaction")
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                      @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(principal.getUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a transaction")
    public TransactionResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(principal.getUserId(), id, request);
    }

    @PatchMapping("/{id}/category")
    @Operation(summary = "Change only the category of a transaction")
    public TransactionResponse recategorise(@AuthenticationPrincipal AppUserDetails principal,
                                            @PathVariable Long id,
                                            @RequestBody RecategoriseRequest request) {
        return transactionService.recategorise(principal.getUserId(), id, request.categoryId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        transactionService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
