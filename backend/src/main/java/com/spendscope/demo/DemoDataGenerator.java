package com.spendscope.demo;

import com.spendscope.account.Account;
import com.spendscope.account.AccountRepository;
import com.spendscope.account.AccountType;
import com.spendscope.budget.Budget;
import com.spendscope.budget.BudgetRepository;
import com.spendscope.categorization.CategorizationService;
import com.spendscope.categorization.CategorizationSession;
import com.spendscope.category.Category;
import com.spendscope.category.CategoryRepository;
import com.spendscope.transaction.Transaction;
import com.spendscope.transaction.TransactionFingerprint;
import com.spendscope.transaction.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a believable nine-month spending history for a demo account.
 *
 * <p>Entirely synthetic. No real user's data is ever copied into a demo
 * account, and the merchant list is public brand names attached to invented
 * amounts.
 *
 * <p>The data is generated from a <em>fixed seed</em>, so every demo account
 * looks the same. That is deliberate: screenshots stay reproducible, and a
 * reviewer clicking "Try demo" sees the same dashboard the README describes.
 *
 * <p>It is also shaped to exercise every feature rather than being uniform
 * noise:
 * <ul>
 *   <li>Exact-amount monthly subscriptions, so recurring detection has
 *       something to find.</li>
 *   <li>A holiday two months back, so period comparison shows a real swing.</li>
 *   <li>An unusually expensive current month in one category, so the anomaly
 *       card is populated.</li>
 *   <li>Budgets set slightly below typical spending, so some bars are over.</li>
 * </ul>
 */
@Component
public class DemoDataGenerator {

    private static final long RANDOM_SEED = 20260905L;
    private static final String CURRENCY = "SGD";
    private static final int MONTHS_OF_HISTORY = 9;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationService categorizationService;

    public DemoDataGenerator(AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             BudgetRepository budgetRepository,
                             CategoryRepository categoryRepository,
                             CategorizationService categorizationService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationService = categorizationService;
    }

    @Transactional
    public void populate(Long userId, LocalDate today) {
        Account debit = accountRepository.save(
                new Account(userId, "DBS Debit", AccountType.CHECKING, CURRENCY));
        Account creditCard = accountRepository.save(
                new Account(userId, "OCBC Credit Card", AccountType.CREDIT_CARD, CURRENCY));
        Account cash = accountRepository.save(
                new Account(userId, "Cash", AccountType.CASH, CURRENCY));

        Random random = new Random(RANDOM_SEED);
        List<Draft> drafts = new ArrayList<>();

        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(MONTHS_OF_HISTORY - 1);

        for (int monthOffset = 0; monthOffset < MONTHS_OF_HISTORY; monthOffset++) {
            YearMonth month = firstMonth.plusMonths(monthOffset);
            boolean isCurrentMonth = month.equals(currentMonth);
            boolean isHolidayMonth = month.equals(currentMonth.minusMonths(2));

            addSalary(drafts, month, debit, today);
            addSubscriptions(drafts, month, creditCard, today);
            addUtilities(drafts, month, debit, today, random);
            addGroceries(drafts, month, debit, today, random);
            addEatingOut(drafts, month, creditCard, cash, today, random);
            addTransport(drafts, month, debit, today, random);
            addShopping(drafts, month, creditCard, today, random, isCurrentMonth);
            addEntertainment(drafts, month, creditCard, today, random);

            if (isHolidayMonth) {
                addHoliday(drafts, month, creditCard, today);
            }
        }

        persist(userId, drafts);
        addBudgets(userId, currentMonth);
    }

    // ------------------------------------------------------------------
    // Spending patterns
    // ------------------------------------------------------------------

    /** Salary on the 25th, the one positive amount in the data. */
    private void addSalary(List<Draft> drafts, YearMonth month, Account account, LocalDate today) {
        add(drafts, account, date(month, 25, today), "SALARY CREDIT ACME PTE LTD", new BigDecimal("4200.00"));
    }

    /**
     * Exact amounts on stable days. This is what the recurring detector is
     * meant to find, so it is generated the way a real subscription behaves.
     */
    private void addSubscriptions(List<Draft> drafts, YearMonth month, Account account, LocalDate today) {
        add(drafts, account, date(month, 3, today), "SPOTIFY P34A9B21", new BigDecimal("-12.98"));
        add(drafts, account, date(month, 15, today), "NETFLIX.COM SINGAPORE", new BigDecimal("-19.98"));
        add(drafts, account, date(month, 8, today), "APPLE.COM/BILL ITUNES", new BigDecimal("-4.98"));
        add(drafts, account, date(month, 20, today), "ADOBE CREATIVE CLOUD", new BigDecimal("-28.98"));
    }

    private void addUtilities(List<Draft> drafts, YearMonth month, Account account, LocalDate today, Random random) {
        add(drafts, account, date(month, 10, today), "SP SERVICES LTD",
                money(-95, 45, random));
        add(drafts, account, date(month, 12, today), "SINGTEL MOBILE POSTPAID",
                money(-42, 8, random));
    }

    private void addGroceries(List<Draft> drafts, YearMonth month, Account account, LocalDate today, Random random) {
        // A weekly big shop plus a couple of top-ups.
        for (int day : new int[]{4, 11, 18, 25}) {
            add(drafts, account, date(month, day, today), "NTUC FAIRPRICE #" + (100 + random.nextInt(80)),
                    money(-78, 30, random));
        }
        add(drafts, account, date(month, 14, today), "COLD STORAGE HOLLAND V", money(-36, 18, random));
        add(drafts, account, date(month, 22, today), "SHENG SIONG SUPERMARKET", money(-24, 12, random));
    }

    private void addEatingOut(List<Draft> drafts,
                              YearMonth month,
                              Account creditCard,
                              Account cash,
                              LocalDate today,
                              Random random) {
        String[] descriptions = {
                "STARBUCKS COFFEE #4821", "MCDONALDS RESTAURANT", "GRABFOOD DELIVERY",
                "YA KUN KAYA TOAST", "TOAST BOX ORCHARD", "KFC SINGAPORE", "DELIVEROO SG"
        };
        int visits = 12 + random.nextInt(8);
        for (int visit = 0; visit < visits; visit++) {
            int day = 1 + random.nextInt(28);
            Account account = random.nextInt(4) == 0 ? cash : creditCard;
            add(drafts, account, date(month, day, today),
                    descriptions[random.nextInt(descriptions.length)],
                    money(-14, 11, random));
        }
    }

    private void addTransport(List<Draft> drafts, YearMonth month, Account account, LocalDate today, Random random) {
        int rides = 10 + random.nextInt(10);
        for (int ride = 0; ride < rides; ride++) {
            int day = 1 + random.nextInt(28);
            String description = random.nextBoolean()
                    ? "GRAB *TRIP " + (1000 + random.nextInt(8000))
                    : "SIMPLYGO TRANSIT";
            add(drafts, account, date(month, day, today), description, money(-11, 9, random));
        }
    }

    /**
     * Shopping is normally modest. The current month gets one large purchase so
     * the anomaly detector has a genuine signal to report rather than an
     * invented one.
     */
    private void addShopping(List<Draft> drafts,
                             YearMonth month,
                             Account account,
                             LocalDate today,
                             Random random,
                             boolean isCurrentMonth) {
        String[] descriptions = {"UNIQLO SINGAPORE", "SHOPEE SG PAYMENT", "AMAZON SG MARKETPLACE",
                "LAZADA SINGAPORE", "MUJI PLAZA SING", "DECATHLON SG"};
        int purchases = 2 + random.nextInt(3);
        for (int purchase = 0; purchase < purchases; purchase++) {
            add(drafts, account, date(month, 2 + random.nextInt(26), today),
                    descriptions[random.nextInt(descriptions.length)],
                    money(-58, 40, random));
        }
        if (isCurrentMonth) {
            // Day 2 so it is inside the month-to-date window whenever the demo
            // is opened.
            add(drafts, account, date(month, 2, today), "AMAZON SG MARKETPLACE LAPTOP STAND SET",
                    new BigDecimal("-689.00"));
        }
    }

    private void addEntertainment(List<Draft> drafts,
                                  YearMonth month,
                                  Account account,
                                  LocalDate today,
                                  Random random) {
        add(drafts, account, date(month, 6 + random.nextInt(20), today), "GOLDEN VILLAGE CINEMAS",
                money(-27, 10, random));
        if (random.nextBoolean()) {
            add(drafts, account, date(month, 6 + random.nextInt(20), today), "STEAMGAMES.COM",
                    money(-32, 20, random));
        }
    }

    /** A holiday two months back, so period comparison has something to show. */
    private void addHoliday(List<Draft> drafts, YearMonth month, Account account, LocalDate today) {
        add(drafts, account, date(month, 8, today), "SINGAPORE AIRLINES TICKET", new BigDecimal("-742.40"));
        add(drafts, account, date(month, 9, today), "AGODA HOTEL BOOKING", new BigDecimal("-586.00"));
        add(drafts, account, date(month, 12, today), "AIRBNB * HMQ4T2", new BigDecimal("-318.50"));
        add(drafts, account, date(month, 13, today), "BOOKING.COM RESERVATION", new BigDecimal("-204.00"));
    }

    /** Budgets set close to typical spending so some are met and some are not. */
    private void addBudgets(Long userId, YearMonth month) {
        budget(userId, month, "Food", new BigDecimal("320.00"));
        budget(userId, month, "Groceries", new BigDecimal("420.00"));
        budget(userId, month, "Transport", new BigDecimal("180.00"));
        budget(userId, month, "Shopping", new BigDecimal("250.00"));
        budget(userId, month, "Subscriptions", new BigDecimal("70.00"));
    }

    private void budget(Long userId, YearMonth month, String categoryName, BigDecimal amount) {
        categoryRepository.findByUserIdIsNullAndNameIgnoreCase(categoryName)
                .ifPresent(category -> budgetRepository.save(
                        new Budget(userId, category.getId(), month, amount)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Resolves a day of the month, or null when that day has not arrived yet.
     *
     * <p>Returning null rather than clamping to today matters. Clamping would
     * stack the rest of the current month's spending onto today, so the partial
     * current month would look like a spending explosion in every
     * period-over-period comparison. Skipping gives a realistic partial month.
     */
    private LocalDate date(YearMonth month, int dayOfMonth, LocalDate today) {
        LocalDate date = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        return date.isAfter(today) ? null : date;
    }

    /** A plausible amount: a centre value plus up to {@code spread} of noise. */
    private BigDecimal money(double centre, double spread, Random random) {
        double value = centre + (random.nextDouble() - 0.5) * 2 * spread;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Ignores drafts whose date has not happened yet (see {@link #date}). */
    private void add(List<Draft> drafts, Account account, LocalDate date, String description, BigDecimal amount) {
        if (date != null) {
            drafts.add(new Draft(account, date, description, amount));
        }
    }

    /**
     * Runs the drafts through the same categorisation and fingerprinting the
     * CSV importer uses, so demo data is indistinguishable from imported data.
     */
    private void persist(Long userId, List<Draft> drafts) {
        CategorizationSession session = categorizationService.openSession(userId);
        List<Transaction> transactions = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            CategorizationSession.Categorisation categorisation = session.categorise(draft.description());
            Long categoryId = categorisation.categoryId();
            if (categoryId == null && draft.amount().signum() > 0) {
                categoryId = categoryRepository.findByUserIdIsNullAndNameIgnoreCase("Income")
                        .map(Category::getId).orElse(null);
            }
            transactions.add(new Transaction(
                    userId,
                    draft.account().getId(),
                    categoryId,
                    draft.date(),
                    draft.description(),
                    categorisation.normalisedDescription(),
                    categorisation.merchant(),
                    draft.amount().setScale(4, RoundingMode.HALF_UP),
                    CURRENCY,
                    TransactionFingerprint.compute(userId, draft.account().getId(), draft.date(),
                            categorisation.normalisedDescription(),
                            draft.amount().setScale(4, RoundingMode.HALF_UP), CURRENCY)));
        }
        transactionRepository.saveAll(transactions);
    }

    private record Draft(Account account, LocalDate date, String description, BigDecimal amount) {
    }
}
