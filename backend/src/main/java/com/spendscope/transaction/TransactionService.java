package com.spendscope.transaction;

import com.spendscope.account.Account;
import com.spendscope.account.AccountRepository;
import com.spendscope.account.AccountService;
import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.categorization.CategorizationService;
import com.spendscope.categorization.CategorizationSession;
import com.spendscope.category.Category;
import com.spendscope.category.CategoryRepository;
import com.spendscope.category.CategoryService;
import com.spendscope.common.Money;
import com.spendscope.common.error.BadRequestException;
import com.spendscope.common.error.NotFoundException;
import com.spendscope.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Transaction CRUD.
 *
 * <p>Responsibilities kept here rather than in the controller: ownership
 * checks, derived fields (normalised description, merchant, fingerprint) and
 * the sanity rules that annotations cannot express.
 */
@Service
public class TransactionService {

    /**
     * A transaction more than a year in the future is almost certainly a typo
     * in the year field, and it would distort every "this year" metric.
     */
    private static final int MAX_YEARS_IN_FUTURE = 1;
    private static final LocalDate EARLIEST_PLAUSIBLE_DATE = LocalDate.of(1970, 1, 1);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final CategorizationService categorizationService;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              AccountService accountService,
                              CategoryService categoryService,
                              CategorizationService categorizationService,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.categorizationService = categorizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, TransactionQuery query) {
        query.validateDateRange();
        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.forUser(userId, query), query.toPageRequest());

        // Names for the whole page are resolved with two queries, not two per row.
        LabelLookup labels = loadLabels(userId);
        return PageResponse.from(page, transaction -> toResponse(transaction, labels));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long transactionId) {
        Transaction transaction = require(userId, transactionId);
        return toResponse(transaction, loadLabels(userId));
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        validate(request);
        Account account = accountService.require(userId, request.accountId());
        Long categoryId = resolveCategoryId(userId, request.categoryId());

        CategorizationSession.Categorisation categorisation =
                categorizationService.categoriseOne(userId, request.description());

        String currency = resolveCurrency(request.currency(), account);
        BigDecimal amount = Money.normalise(request.amount());
        String merchant = resolveMerchant(request.merchant(), categorisation.merchant());

        Transaction transaction = new Transaction(
                userId,
                account.getId(),
                categoryId != null ? categoryId : categorisation.categoryId(),
                request.date(),
                request.description().trim(),
                categorisation.normalisedDescription(),
                merchant,
                amount,
                currency,
                TransactionFingerprint.compute(userId, account.getId(), request.date(),
                        categorisation.normalisedDescription(), amount, currency));

        Transaction saved = transactionRepository.save(transaction);
        auditService.record(userId, AuditEventType.TRANSACTION_CREATED, Map.of("transactionId", saved.getId()));
        return toResponse(saved, loadLabels(userId));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long transactionId, TransactionRequest request) {
        validate(request);
        Transaction transaction = require(userId, transactionId);
        Account account = accountService.require(userId, request.accountId());

        // An explicit null category is a real instruction ("un-categorise
        // this"), so the automatic categoriser is only consulted for the
        // description-derived fields, never to override the user's choice.
        Long categoryId = resolveCategoryId(userId, request.categoryId());

        String normalisedDescription = categorizationService.normalise(request.description());
        String currency = resolveCurrency(request.currency(), account);
        BigDecimal amount = Money.normalise(request.amount());

        transaction.setAccountId(account.getId());
        transaction.setCategoryId(categoryId);
        transaction.setDate(request.date());
        transaction.setDescription(request.description().trim());
        transaction.setNormalizedDescription(normalisedDescription);
        transaction.setMerchant(resolveMerchant(request.merchant(),
                categorizationService.categoriseOne(userId, request.description()).merchant()));
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        // The fingerprint describes the current field values, so editing any of
        // them has to recompute it or future duplicate checks would use stale
        // identity.
        transaction.setFingerprint(TransactionFingerprint.compute(
                userId, account.getId(), request.date(), normalisedDescription, amount, currency));

        auditService.record(userId, AuditEventType.TRANSACTION_UPDATED, Map.of("transactionId", transactionId));
        return toResponse(transaction, loadLabels(userId));
    }

    /** Assigning a category is the common case, so it gets its own small endpoint. */
    @Transactional
    public TransactionResponse recategorise(Long userId, Long transactionId, Long categoryId) {
        Transaction transaction = require(userId, transactionId);
        transaction.setCategoryId(resolveCategoryId(userId, categoryId));
        auditService.record(userId, AuditEventType.TRANSACTION_UPDATED,
                Map.of("transactionId", transactionId, "categoryId", String.valueOf(categoryId)));
        return toResponse(transaction, loadLabels(userId));
    }

    @Transactional
    public void delete(Long userId, Long transactionId) {
        Transaction transaction = require(userId, transactionId);
        transactionRepository.delete(transaction);
        auditService.record(userId, AuditEventType.TRANSACTION_DELETED, Map.of("transactionId", transactionId));
    }

    /**
     * The one place a transaction is loaded by id.
     *
     * <p>Note what it is <em>not</em>: {@code findById(transactionId)} followed
     * by a check. The user id is part of the WHERE clause, so a row belonging to
     * someone else is never loaded into memory in the first place, and a
     * forgotten check cannot leak it.
     */
    @Transactional(readOnly = true)
    public Transaction require(Long userId, Long transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> NotFoundException.of("Transaction", transactionId));
    }

    private void validate(TransactionRequest request) {
        if (request.amount().signum() == 0) {
            throw new BadRequestException("Amount must not be zero");
        }
        LocalDate latestAllowed = LocalDate.now().plusYears(MAX_YEARS_IN_FUTURE);
        if (request.date().isAfter(latestAllowed)) {
            throw new BadRequestException("Date must not be more than "
                    + MAX_YEARS_IN_FUTURE + " year in the future");
        }
        if (request.date().isBefore(EARLIEST_PLAUSIBLE_DATE)) {
            throw new BadRequestException("Date must not be before " + EARLIEST_PLAUSIBLE_DATE);
        }
    }

    /** Null stays null (uncategorised); anything else must be a category the user can see. */
    private Long resolveCategoryId(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryService.require(userId, categoryId).getId();
    }

    private String resolveCurrency(String requested, Account account) {
        if (requested == null || requested.isBlank()) {
            return account.getCurrency();
        }
        return requested.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveMerchant(String requested, String detected) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return detected;
    }

    private TransactionResponse toResponse(Transaction transaction, LabelLookup labels) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                labels.accountNames().get(transaction.getAccountId()),
                transaction.getCategoryId(),
                transaction.getCategoryId() == null ? null : labels.categoryNames().get(transaction.getCategoryId()),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCreatedAt());
    }

    /**
     * Account and category display names for one user.
     *
     * <p>Loading these once per request is the fix for the classic N+1: mapping
     * a 25-row page would otherwise issue 50 extra selects.
     */
    private LabelLookup loadLabels(Long userId) {
        Map<Long, String> accountNames = new HashMap<>();
        accountRepository.findByUserIdOrderByArchivedAscNameAsc(userId)
                .forEach(account -> accountNames.put(account.getId(), account.getName()));

        Map<Long, String> categoryNames = new HashMap<>();
        categoryRepository.findAccessible(userId)
                .forEach(category -> categoryNames.put(category.getId(), category.getName()));

        return new LabelLookup(accountNames, categoryNames);
    }

    private record LabelLookup(Map<Long, String> accountNames, Map<Long, String> categoryNames) {
    }

    /** Exposed for the import pipeline, which needs the same maps. */
    @Transactional(readOnly = true)
    public Map<Long, String> categoryNamesFor(Long userId) {
        return categoryRepository.findAccessible(userId).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName,
                        (first, second) -> first));
    }

    /** @return the ids of every category the user may reference. */
    @Transactional(readOnly = true)
    public Set<Long> accessibleCategoryIds(Long userId) {
        return categoryRepository.findAccessible(userId).stream()
                .map(Category::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Small helper used by tests and the demo generator. */
    @Transactional(readOnly = true)
    public long countFor(Long userId) {
        return transactionRepository.countByUserId(userId);
    }

    static <T, K> Map<K, T> indexBy(List<T> items, Function<T, K> keyExtractor) {
        Map<K, T> index = new HashMap<>();
        items.forEach(item -> index.put(keyExtractor.apply(item), item));
        return index;
    }
}
