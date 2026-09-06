package com.spendscope.common;

import com.spendscope.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the schema Flyway produces, and that the database enforces the rules
 * the application assumes.
 *
 * <p>A constraint the application checks but the database does not is a
 * constraint that disappears the moment anything writes outside the
 * application - a migration, a support script, a second service.
 */
@DisplayName("Schema and migrations")
class SchemaAndMigrationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("every migration applied successfully")
    void migrationsApplied() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSizeGreaterThanOrEqualTo(8);
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
        assertThat(history).extracting(row -> row.get("version"))
                .contains("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    @DisplayName("Hibernate validates cleanly against the migrated schema")
    void hibernateSchemaValidationPasses() {
        // The context started at all, which with ddl-auto=validate means every
        // entity mapping matches a real column of a compatible type. This test
        // documents that guarantee explicitly.
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("creates every expected table")
    void createsAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains("users", "accounts", "categories", "transactions",
                "budgets", "dashboard_widgets", "import_jobs", "audit_events");
    }

    @Test
    @DisplayName("seeds the built-in categories exactly once")
    void seedsBuiltInCategories() {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM categories WHERE user_id IS NULL ORDER BY name", String.class);

        assertThat(names).containsExactlyInAnyOrder("Food", "Groceries", "Transport", "Shopping",
                "Entertainment", "Subscriptions", "Utilities", "Healthcare", "Travel", "Education",
                "Income", "Other");
    }

    @Test
    @DisplayName("stores money as NUMERIC, never as a floating point type")
    void moneyColumnsAreNumeric() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE column_name = 'amount' AND table_schema = 'public'
                """);

        assertThat(columns).isNotEmpty();
        assertThat(columns).allSatisfy(column -> {
            assertThat(column.get("data_type")).isEqualTo("numeric");
            assertThat(column.get("numeric_precision")).isEqualTo(19);
            assertThat(column.get("numeric_scale")).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("timestamps are timezone-aware")
    void timestampsAreTimezoneAware() {
        List<String> types = jdbcTemplate.queryForList("""
                SELECT DISTINCT data_type FROM information_schema.columns
                WHERE column_name IN ('created_at', 'updated_at', 'occurred_at', 'completed_at')
                  AND table_schema = 'public'
                """, String.class);

        assertThat(types).containsExactly("timestamp with time zone");
    }

    @Test
    @DisplayName("creates the indexes the query plans depend on")
    void createsExpectedIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'transactions'", String.class);

        assertThat(indexes).contains(
                "idx_transactions_user_date",
                "idx_transactions_user_category_date",
                "idx_transactions_user_account_date",
                "idx_transactions_user_fingerprint");
    }

    @Test
    @DisplayName("rejects a duplicate email at the database level")
    void enforcesUniqueEmail() {
        jdbcTemplate.update("INSERT INTO users (email, password_hash) VALUES (?, ?)",
                "unique@example.com", "{bcrypt}$2a$10$abcdefghijklmnopqrstuv");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                "unique@example.com", "{bcrypt}$2a$10$abcdefghijklmnopqrstuv"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a zero transaction amount at the database level")
    void enforcesNonZeroAmount() {
        long userId = insertUser("zero@example.com");
        long accountId = insertAccount(userId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO transactions
                    (user_id, account_id, date, description, normalized_description, amount, currency, fingerprint)
                VALUES (?, ?, DATE '2026-08-01', 'X', 'X', 0, 'SGD', 'f')
                """, userId, accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a budget month that is not the first of a month")
    void enforcesBudgetMonthIsFirstDay() {
        long userId = insertUser("budget@example.com");
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, month, amount) VALUES (?, ?, DATE '2026-09-15', 100)",
                userId, categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a non-positive budget amount")
    void enforcesPositiveBudget() {
        long userId = insertUser("negbudget@example.com");
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO budgets (user_id, category_id, month, amount) VALUES (?, ?, DATE '2026-09-01', -5)",
                userId, categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects two accounts whose names differ only by case")
    void enforcesCaseInsensitiveAccountNames() {
        long userId = insertUser("accounts@example.com");
        jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type, currency) VALUES (?, 'DBS Debit', 'CHECKING', 'SGD')",
                userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type, currency) VALUES (?, 'dbs debit', 'CHECKING', 'SGD')",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("allows the same account name for two different users")
    void accountNamesAreUniquePerUser() {
        long first = insertUser("first@example.com");
        long second = insertUser("second@example.com");

        jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type, currency) VALUES (?, 'DBS Debit', 'CHECKING', 'SGD')",
                first);
        jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type, currency) VALUES (?, 'DBS Debit', 'CHECKING', 'SGD')",
                second);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE name = 'DBS Debit'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("allows a duplicate fingerprint - duplicates are reported, not blocked")
    void fingerprintIsIndexedButNotUnique() {
        long userId = insertUser("fingerprint@example.com");
        long accountId = insertAccount(userId);

        // Buying the same coffee twice on the same day is legitimate. A unique
        // constraint here would make a manual entry impossible.
        for (int index = 0; index < 2; index++) {
            jdbcTemplate.update("""
                    INSERT INTO transactions
                        (user_id, account_id, date, description, normalized_description, amount, currency, fingerprint)
                    VALUES (?, ?, DATE '2026-08-01', 'COFFEE', 'COFFEE', -7.20, 'SGD', 'samefingerprint')
                    """, userId, accountId);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE fingerprint = 'samefingerprint'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("deleting a user cascades to everything they own")
    void deletingUserCascades() {
        long userId = insertUser("cascade@example.com");
        long accountId = insertAccount(userId);
        jdbcTemplate.update("""
                INSERT INTO transactions
                    (user_id, account_id, date, description, normalized_description, amount, currency, fingerprint)
                VALUES (?, ?, DATE '2026-08-01', 'X', 'X', -1, 'SGD', 'fp')
                """, userId, accountId);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    @DisplayName("deleting a category leaves its transactions, uncategorised")
    void deletingCategorySetsTransactionsNull() {
        long userId = insertUser("categorydelete@example.com");
        long accountId = insertAccount(userId);
        jdbcTemplate.update(
                "INSERT INTO categories (user_id, name, type) VALUES (?, 'Temporary', 'EXPENSE')", userId);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id = ? AND name = 'Temporary'", Long.class, userId);

        jdbcTemplate.update("""
                INSERT INTO transactions
                    (user_id, account_id, category_id, date, description, normalized_description,
                     amount, currency, fingerprint)
                VALUES (?, ?, ?, DATE '2026-08-01', 'X', 'X', -1, 'SGD', 'fp2')
                """, userId, accountId, categoryId);

        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);

        // The transaction survives; only its category is cleared. Losing a
        // user's financial history because they tidied up a label would be
        // unacceptable.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT category_id FROM transactions WHERE user_id = ?", Long.class, userId)).isNull();
    }

    private long insertUser(String email) {
        jdbcTemplate.update("INSERT INTO users (email, password_hash) VALUES (?, ?)",
                email, "{bcrypt}$2a$10$abcdefghijklmnopqrstuv");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertAccount(long userId) {
        jdbcTemplate.update(
                "INSERT INTO accounts (user_id, name, type, currency) VALUES (?, 'Test', 'CHECKING', 'SGD')",
                userId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ?", Long.class, userId);
    }
}
