package com.spendscope.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Transaction persistence and the aggregate queries the analytics layer runs on
 * top of it.
 *
 * <p>Two conventions hold throughout:
 * <ol>
 *   <li><b>Every query filters by {@code userId}.</b> Not "most". The
 *       aggregate queries below would leak another user's totals if they did
 *       not, and a totals endpoint leaking is just as bad as a detail one.</li>
 *   <li><b>"Spending" means {@code amount &lt; 0}, reported positive.</b>
 *       Income and refunds are excluded from spending metrics rather than
 *       quietly cancelling them out, because a EUR 3,000 salary would otherwise
 *       make a month look like it had negative spending.</li>
 * </ol>
 *
 * <p>The time-bucketed queries are native SQL because {@code date_trunc} has no
 * JPQL equivalent, and PostgreSQL is the only database this application targets
 * (see docs/decisions.md). They return {@code Object[]} rows which
 * {@code AnalyticsService} maps explicitly - verbose, but it makes the column
 * order visible at the mapping site instead of relying on alias-matching magic.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    // ------------------------------------------------------------------
    // Ownership-scoped lookups
    // ------------------------------------------------------------------

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndAccountId(Long userId, Long accountId);

    long countByUserId(Long userId);

    /** Used by duplicate detection to check a whole CSV batch in one round trip. */
    List<Transaction> findByUserIdAndFingerprintIn(Long userId, Collection<String> fingerprints);

    // ------------------------------------------------------------------
    // Scalar metrics
    // ------------------------------------------------------------------

    @Query("""
            SELECT COALESCE(SUM(-t.amount), 0) FROM Transaction t
            WHERE t.userId = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            """)
    BigDecimal sumSpending(@Param("userId") Long userId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.userId = :userId AND t.date BETWEEN :from AND :to AND t.amount > 0
            """)
    BigDecimal sumIncome(@Param("userId") Long userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.userId = :userId AND t.date BETWEEN :from AND :to
            """)
    long countInPeriod(@Param("userId") Long userId,
                       @Param("from") LocalDate from,
                       @Param("to") LocalDate to);

    /** Spending in one category, used for budget progress. */
    @Query("""
            SELECT COALESCE(SUM(-t.amount), 0) FROM Transaction t
            WHERE t.userId = :userId AND t.categoryId = :categoryId
              AND t.date BETWEEN :from AND :to AND t.amount < 0
            """)
    BigDecimal sumSpendingForCategory(@Param("userId") Long userId,
                                      @Param("categoryId") Long categoryId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    // ------------------------------------------------------------------
    // Grouped aggregates. Each row is [label/key, total, count].
    // ------------------------------------------------------------------

    /** @return rows of [categoryId (Long, nullable), categoryName (String), total (BigDecimal), count (Long)] */
    @Query(value = """
            SELECT t.category_id       AS category_id,
                   COALESCE(c.name, 'Uncategorised') AS category_name,
                   SUM(-t.amount)      AS total,
                   COUNT(*)            AS txn_count
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            GROUP BY t.category_id, c.name
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> spendingByCategory(@Param("userId") Long userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** @return rows of [merchant (String), total (BigDecimal), count (Long)] */
    @Query(value = """
            SELECT COALESCE(t.merchant, 'Unknown') AS merchant,
                   SUM(-t.amount)                  AS total,
                   COUNT(*)                        AS txn_count
            FROM transactions t
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            GROUP BY COALESCE(t.merchant, 'Unknown')
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> spendingByMerchant(@Param("userId") Long userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** @return rows of [accountId (Long), accountName (String), total (BigDecimal), count (Long)] */
    @Query(value = """
            SELECT t.account_id   AS account_id,
                   a.name         AS account_name,
                   SUM(-t.amount) AS total,
                   COUNT(*)       AS txn_count
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            GROUP BY t.account_id, a.name
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> spendingByAccount(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Time-bucketed spending.
     *
     * <p>{@code :truncateTo} is one of {@code day}, {@code week}, {@code month}
     * and is supplied by the enum {@code GroupBy}, never by a client. It is
     * still bound as a parameter rather than concatenated, so even a future
     * caller that gets it wrong cannot inject SQL.
     *
     * @return rows of [bucket (java.sql.Date), total (BigDecimal), count (Long)]
     */
    @Query(value = """
            SELECT date_trunc(CAST(:truncateTo AS text), t.date)::date AS bucket,
                   SUM(-t.amount)                                      AS total,
                   COUNT(*)                                            AS txn_count
            FROM transactions t
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> spendingByTimeBucket(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("truncateTo") String truncateTo);

    /**
     * Monthly spending per category, used by the anomaly detector to build a
     * per-category baseline in a single query.
     *
     * @return rows of [categoryId (Long), categoryName (String),
     *         month (java.sql.Date), total (BigDecimal)]
     */
    @Query(value = """
            SELECT t.category_id                      AS category_id,
                   COALESCE(c.name, 'Uncategorised')  AS category_name,
                   date_trunc('month', t.date)::date  AS month,
                   SUM(-t.amount)                     AS total
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
            GROUP BY t.category_id, c.name, month
            ORDER BY category_name ASC, month ASC
            """, nativeQuery = true)
    List<Object[]> monthlySpendingByCategory(@Param("userId") Long userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Outgoing transactions with a known merchant, ordered for the recurring
     * expense detector. Projected to the four columns the detector needs so a
     * year of history does not have to be hydrated into entities.
     *
     * @return rows of [merchant (String), date (java.sql.Date),
     *         amount (BigDecimal), categoryId (Long, nullable)]
     */
    @Query(value = """
            SELECT t.merchant   AS merchant,
                   t.date       AS date,
                   -t.amount    AS amount,
                   t.category_id AS category_id
            FROM transactions t
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to
              AND t.amount < 0 AND t.merchant IS NOT NULL
            ORDER BY t.merchant ASC, t.date ASC
            """, nativeQuery = true)
    List<Object[]> outgoingWithMerchant(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /**
     * Monthly spending per category, counting only the first
     * {@code maxDayOfMonth} days of each month.
     *
     * <p>This is what makes a like-for-like anomaly comparison possible. On the
     * 5th of the month, comparing five days of spending against six whole
     * previous months would flag nothing; comparing it against the first five
     * days of each of those months is a fair question.
     *
     * @return rows of [categoryId (Long, nullable), categoryName (String),
     *         month (java.sql.Date), total (BigDecimal)]
     */
    @Query(value = """
            SELECT t.category_id                      AS category_id,
                   COALESCE(c.name, 'Uncategorised')  AS category_name,
                   date_trunc('month', t.date)::date  AS month,
                   SUM(-t.amount)                     AS total
            FROM transactions t
            LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
              AND EXTRACT(DAY FROM t.date) <= :maxDayOfMonth
            GROUP BY t.category_id, c.name, month
            ORDER BY category_name ASC, month ASC
            """, nativeQuery = true)
    List<Object[]> monthlySpendingByCategoryUpToDay(@Param("userId") Long userId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("maxDayOfMonth") int maxDayOfMonth);

    /** Earliest transaction date, used to bound "all time" style questions. */
    @Query("SELECT MIN(t.date) FROM Transaction t WHERE t.userId = :userId")
    LocalDate findEarliestDate(@Param("userId") Long userId);
}
