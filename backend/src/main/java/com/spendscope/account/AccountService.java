package com.spendscope.account;

import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.common.error.ConflictException;
import com.spendscope.common.error.NotFoundException;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Account CRUD, always scoped to the authenticated user.
 *
 * <p>Every public method takes {@code userId} as its first argument. That is a
 * convention with teeth: it makes an unscoped query visibly wrong at the call
 * site rather than something a reviewer has to notice by absence.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId, boolean includeArchived) {
        List<Account> accounts = includeArchived
                ? accountRepository.findByUserIdOrderByArchivedAscNameAsc(userId)
                : accountRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId);
        return accounts.stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long userId, Long accountId) {
        return AccountResponse.from(require(userId, accountId));
    }

    /**
     * Loads an account or fails.
     *
     * <p>Used by other domains (transactions, imports) that need to prove the
     * account belongs to the caller before referencing it.
     */
    @Transactional(readOnly = true)
    public Account require(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> NotFoundException.of("Account", accountId));
    }

    @Transactional
    public AccountResponse create(Long userId, AccountRequest request) {
        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("An account named '" + name + "' already exists");
        }
        Account account = new Account(userId, name, request.type(), normaliseCurrency(request.currency()));
        Account saved = accountRepository.save(account);
        auditService.record(userId, AuditEventType.ACCOUNT_CREATED, Map.of("accountId", saved.getId()));
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(Long userId, Long accountId, AccountRequest request) {
        Account account = require(userId, accountId);
        String name = request.name().trim();
        if (!account.getName().equalsIgnoreCase(name)
                && accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("An account named '" + name + "' already exists");
        }
        account.setName(name);
        account.setType(request.type());
        account.setCurrency(normaliseCurrency(request.currency()));
        auditService.record(userId, AuditEventType.ACCOUNT_UPDATED, Map.of("accountId", accountId));
        return AccountResponse.from(account);
    }

    /**
     * Hides the account without touching its history.
     *
     * <p>Archiving is the default because deleting an account would cascade
     * into every transaction ever recorded against it, silently destroying the
     * analytics the user came for.
     */
    @Transactional
    public AccountResponse archive(Long userId, Long accountId, boolean archived) {
        Account account = require(userId, accountId);
        account.setArchived(archived);
        if (archived) {
            auditService.record(userId, AuditEventType.ACCOUNT_ARCHIVED, Map.of("accountId", accountId));
        }
        return AccountResponse.from(account);
    }

    /**
     * Permanently removes an account.
     *
     * <p>Refused once the account has transactions: the caller must archive
     * instead. The alternative - cascading the delete - is exactly the "silently
     * deleting everything" behaviour this design avoids.
     */
    @Transactional
    public void delete(Long userId, Long accountId) {
        Account account = require(userId, accountId);
        long transactionCount = transactionRepository.countByUserIdAndAccountId(userId, accountId);
        if (transactionCount > 0) {
            throw new ConflictException(
                    "This account has " + transactionCount + " transactions. Archive it instead of deleting it.");
        }
        accountRepository.delete(account);
        auditService.record(userId, AuditEventType.ACCOUNT_DELETED, Map.of("accountId", accountId));
    }

    private String normaliseCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
