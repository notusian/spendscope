package com.spendscope.recurring;

import com.spendscope.account.Account;
import com.spendscope.account.AccountRepository;
import com.spendscope.analytics.DateRange;
import com.spendscope.analytics.PeriodResolver;
import com.spendscope.category.Category;
import com.spendscope.category.CategoryRepository;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a user's payment history and runs the detector over it.
 *
 * <p>The detector itself knows nothing about JPA or users, which is what lets
 * it be unit tested with a handful of in-memory payments.
 */
@Service
public class RecurringExpenseService {

    /**
     * How far back to look. A year is enough to catch annual subscriptions, and
     * short enough that the query stays cheap.
     */
    private static final int LOOKBACK_MONTHS = 12;

    private static final String DEFAULT_CURRENCY = "SGD";

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final RecurringExpenseDetector detector;
    private final PeriodResolver periodResolver;

    public RecurringExpenseService(TransactionRepository transactionRepository,
                                   CategoryRepository categoryRepository,
                                   AccountRepository accountRepository,
                                   RecurringExpenseDetector detector,
                                   PeriodResolver periodResolver) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.detector = detector;
        this.periodResolver = periodResolver;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> detect(Long userId) {
        LocalDate today = periodResolver.today();
        DateRange window = new DateRange(today.minusMonths(LOOKBACK_MONTHS), today);

        List<RecurringExpenseDetector.Payment> payments = new ArrayList<>();
        for (Object[] row : transactionRepository.outgoingWithMerchant(userId, window.start(), window.end())) {
            payments.add(new RecurringExpenseDetector.Payment(
                    (String) row[0],
                    toLocalDate(row[1]),
                    (BigDecimal) row[2],
                    row[3] == null ? null : ((Number) row[3]).longValue()));
        }

        Map<Long, String> categoryNames = categoryNames(userId);
        String currency = currencyFor(userId);

        return detector.detect(payments).stream()
                .map(expense -> toResponse(expense, categoryNames, currency))
                .toList();
    }

    private RecurringExpenseResponse toResponse(RecurringExpense expense,
                                                Map<Long, String> categoryNames,
                                                String currency) {
        return new RecurringExpenseResponse(
                expense.merchant(),
                expense.categoryId(),
                expense.categoryId() == null ? null : categoryNames.get(expense.categoryId()),
                expense.cadence(),
                expense.typicalAmount(),
                expense.monthlyCost(),
                expense.annualCost(),
                expense.occurrences(),
                expense.firstSeen(),
                expense.lastSeen(),
                expense.nextExpectedDate(),
                expense.confidence(),
                explain(expense),
                currency);
    }

    /**
     * A sentence the user can check against their own memory. If the
     * explanation does not match what they know, the detection is wrong and
     * they can see why.
     */
    private String explain(RecurringExpense expense) {
        return "%d payments of about %s %s since %s, %.0f%% of them %s apart"
                .formatted(
                        expense.occurrences(),
                        expense.typicalAmount(),
                        expense.cadence().name().toLowerCase(java.util.Locale.ROOT),
                        expense.firstSeen(),
                        expense.intervalConsistency() * 100,
                        expense.cadence().minDays() + "-" + expense.cadence().maxDays() + " days");
    }

    private Map<Long, String> categoryNames(Long userId) {
        Map<Long, String> names = new HashMap<>();
        for (Category category : categoryRepository.findAccessible(userId)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    private String currencyFor(Long userId) {
        List<Account> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByNameAsc(userId);
        return accounts.isEmpty() ? DEFAULT_CURRENCY : accounts.get(0).getCurrency();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
