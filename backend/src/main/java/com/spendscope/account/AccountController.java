package com.spendscope.account;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List the caller's accounts")
    public List<AccountResponse> list(@AuthenticationPrincipal AppUserDetails principal,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(principal.getUserId(), includeArchived);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one account owned by the caller")
    public AccountResponse get(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        return accountService.get(principal.getUserId(), id);
    }

    @PostMapping
    @Operation(summary = "Create an account")
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                  @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(principal.getUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AccountRequest request) {
        return accountService.update(principal.getUserId(), id, request);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive an account, keeping its transaction history")
    public AccountResponse archive(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        return accountService.archive(principal.getUserId(), id, true);
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Restore an archived account")
    public AccountResponse unarchive(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        return accountService.archive(principal.getUserId(), id, false);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an empty account (accounts with history must be archived)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id) {
        accountService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
