package com.spendscope.budget;

import com.spendscope.account.Account;
import com.spendscope.account.AccountRepository;
import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.category.Category;
import com.spendscope.category.CategoryRepository;
import com.spendscope.category.CategoryService;
import com.spendscope.common.Money;
import com.spendscope.common.Percentages;
import com.spendscope.common.error.ConflictException;
import com.spendscope.common.error.NotFoundException;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly category budgets and their progress.
 *
 * <p>Progress is computed on read from the transaction table, which is the
 * whole reason a budget can never disagree with the transactions it summarises.
 */
@Service
public class BudgetService {

    private static final String DEFAULT_CURRENCY = "SGD";

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CategoryService categoryService;
    private final AuditService auditService;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository,
                         TransactionRepository transactionRepository,
                         CategoryRepository categoryRepository,
                         AccountRepository accountRepository,
                         CategoryService categoryService,
                         AuditService auditService,
                         Clock clock) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.categoryService = categoryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * @param month null means the current month
     */
    @Transactional(readOnly = true)
    public List<BudgetProgressResponse> listForMonth(Long userId, YearMonth month) {
        YearMonth target = month == null ? YearMonth.now(clock) : month;
        List<Budget> budgets = budgetRepository.findByUserIdAndMonth(userId, target.atDay(1));

        Map<Long, String> categoryNames = categoryNames(userId);
        String currency = currencyFor(userId);

        return budgets.stream()
                .map(budget -> toProgress(userId, budget, categoryNames, currency))
                .sorted(Comparator.comparing(BudgetProgressResponse::categoryName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Transactional
    public BudgetProgressResponse create(Long userId, BudgetRequest request) {
        YearMonth month = parseMonth(request.month());
        Category category = categoryService.require(userId, request.categoryId());

        budgetRepository.findByUserIdAndCategoryIdAndMonth(userId, category.getId(), month.atDay(1))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "A budget for " + category.getName() + " in " + request.month() + " already exists");
                });

        Budget budget = budgetRepository.save(
                new Budget(userId, category.getId(), month, Money.normalise(request.amount())));
        auditService.record(userId, AuditEventType.BUDGET_CREATED, Map.of("budgetId", budget.getId()));
        return toProgress(userId, budget, categoryNames(userId), currencyFor(userId));
    }

    @Transactional
    public BudgetProgressResponse update(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = require(userId, budgetId);
        // Only the amount is editable. Moving a budget to a different category
        // or month is a different budget, and creating one keeps the history of
        // what was actually budgeted at the time.
        budget.setAmount(Money.normalise(request.amount()));
        auditService.record(userId, AuditEventType.BUDGET_UPDATED, Map.of("budgetId", budgetId));
        return toProgress(userId, budget, categoryNames(userId), currencyFor(userId));
    }

    @Transactional
    public void delete(Long userId, Long budgetId) {
        budgetRepository.delete(require(userId, budgetId));
        auditService.record(userId, AuditEventType.BUDGET_DELETED, Map.of("budgetId", budgetId));
    }

    private Budget require(Long userId, Long budgetId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> NotFoundException.of("Budget", budgetId));
    }

    /**
     * Derives spend, remaining and percentage for one budget.
     *
     * <p>Package-private arithmetic lives in {@link #progressOf} so it can be
     * unit tested without a database.
     */
    private BudgetProgressResponse toProgress(Long userId,
                                              Budget budget,
                                              Map<Long, String> categoryNames,
                                              String currency) {
        YearMonth month = budget.getYearMonth();
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        BigDecimal spent = Money.zeroIfNull(transactionRepository.sumSpendingForCategory(
                userId, budget.getCategoryId(), firstDay, lastDay));

        Progress progress = progressOf(budget.getAmount(), spent);

        return new BudgetProgressResponse(
                budget.getId(),
                budget.getCategoryId(),
                categoryNames.get(budget.getCategoryId()),
                month.toString(),
                budget.getAmount(),
                spent,
                progress.remaining(),
                progress.percentageUsed(),
                progress.exceeded(),
                currency);
    }

    /**
     * The budget arithmetic, isolated from persistence.
     *
     * <p>{@code remaining} is allowed to go negative on purpose: clamping it at
     * zero would hide exactly the number the user most needs to see.
     */
    static Progress progressOf(BigDecimal budgetAmount, BigDecimal spent) {
        BigDecimal remaining = budgetAmount.subtract(spent);
        BigDecimal percentageUsed = Percentages.ratio(spent, budgetAmount);
        boolean exceeded = spent.compareTo(budgetAmount) > 0;
        return new Progress(remaining, percentageUsed, exceeded);
    }

    record Progress(BigDecimal remaining, BigDecimal percentageUsed, boolean exceeded) {
    }

    private Map<Long, String> categoryNames(Long userId) {
        Map<Long, String> names = new HashMap<>();
        categoryRepository.findAccessible(userId).forEach(category -> names.put(category.getId(), category.getName()));
        return names;
    }

    private String currencyFor(Long userId) {
        List<Account> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId);
        return accounts.isEmpty() ? DEFAULT_CURRENCY : accounts.get(0).getCurrency();
    }

    static YearMonth parseMonth(String month) {
        return YearMonth.parse(month);
    }
}
