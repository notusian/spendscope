package com.spendscope.transaction;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Transaction API")
class TransactionApiIT extends AbstractIntegrationTest {

    private ApiTestClient api;
    private TestUser user;
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper);
        user = api.registerAndLogin();
        accountId = api.createAccount(user, "DBS Debit");
    }

    @Test
    @DisplayName("creates a transaction and categorises it automatically")
    void createsAndAutoCategorises() throws Exception {
        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "date", "2026-08-01",
                                "description", "STARBUCKS COFFEE #4821",
                                "amount", -7.20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchant").value("Starbucks"))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andExpect(jsonPath("$.accountName").value("DBS Debit"));
    }

    @Test
    @DisplayName("an explicit category beats the automatic one")
    void explicitCategoryWins() throws Exception {
        Long groceriesId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Groceries'", Long.class);

        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "categoryId", groceriesId,
                                "date", "2026-08-01",
                                "description", "STARBUCKS COFFEE",
                                "amount", -7.20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Groceries"));
    }

    @Test
    @DisplayName("inherits the account currency when none is given")
    void inheritsAccountCurrency() throws Exception {
        Long usdAccount = api.read(mockMvc.perform(api.post(user, "/api/accounts")
                        .content(api.json(Map.of("name", "US Card", "type", "CREDIT_CARD", "currency", "USD"))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", usdAccount,
                                "date", "2026-08-01",
                                "description", "AMAZON",
                                "amount", -20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("rejects a zero amount")
    void rejectsZeroAmount() throws Exception {
        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "date", "2026-08-01",
                                "description", "NOTHING",
                                "amount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("rejects a missing required field with a per-field message")
    void rejectsMissingFields() throws Exception {
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("accountId", accountId);
        incomplete.put("date", "2026-08-01");
        incomplete.put("description", "NO AMOUNT");

        mockMvc.perform(api.post(user, "/api/transactions").content(api.json(incomplete)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("Amount is required"));
    }

    @Test
    @DisplayName("rejects a date absurdly far in the future")
    void rejectsFarFutureDate() throws Exception {
        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "date", "2099-01-01",
                                "description", "TYPO IN YEAR",
                                "amount", -5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects an unknown currency code")
    void rejectsInvalidCurrency() throws Exception {
        mockMvc.perform(api.post(user, "/api/transactions")
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "date", "2026-08-01",
                                "description", "SOMETHING",
                                "amount", -5,
                                "currency", "ZZZ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());
    }

    @Test
    @DisplayName("updates a transaction and recomputes its fingerprint")
    void updateRecomputesFingerprint() throws Exception {
        Long id = api.createTransaction(user, accountId, "2026-08-01", "STARBUCKS", "-7.20");
        String before = jdbcTemplate.queryForObject(
                "SELECT fingerprint FROM transactions WHERE id = ?", String.class, id);

        mockMvc.perform(api.put(user, "/api/transactions/" + id)
                        .content(api.json(Map.of(
                                "accountId", accountId,
                                "date", "2026-08-02",
                                "description", "MCDONALDS",
                                "amount", -12.50))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("McDonald's"));

        String after = jdbcTemplate.queryForObject(
                "SELECT fingerprint FROM transactions WHERE id = ?", String.class, id);

        // A stale fingerprint would make future duplicate checks compare
        // against fields the row no longer has.
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("clears a category when asked to")
    void clearsCategory() throws Exception {
        Long id = api.createTransaction(user, accountId, "2026-08-01", "STARBUCKS", "-7.20");

        mockMvc.perform(api.patch(user, "/api/transactions/" + id + "/category")
                        .content("{\"categoryId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    @DisplayName("deletes a transaction")
    void deletesTransaction() throws Exception {
        Long id = api.createTransaction(user, accountId, "2026-08-01", "STARBUCKS", "-7.20");

        mockMvc.perform(api.delete(user, "/api/transactions/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(api.get(user, "/api/transactions/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("paginates and orders newest first by default")
    void paginates() throws Exception {
        for (int day = 1; day <= 30; day++) {
            api.createTransaction(user, accountId,
                    String.format("2026-08-%02d", day), "PURCHASE " + day, "-10.00");
        }

        mockMvc.perform(api.get(user, "/api/transactions?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(30))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].date").value("2026-08-30"));

        mockMvc.perform(api.get(user, "/api/transactions?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("clamps an oversized page size instead of returning everything")
    void clampsPageSize() throws Exception {
        // ?size=1000000 must not be a way to make the server materialise every
        // row a user owns.
        mockMvc.perform(api.get(user, "/api/transactions?size=100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
    }

    @Test
    @DisplayName("filters by date range, account and category")
    void filters() throws Exception {
        api.createTransaction(user, accountId, "2026-07-15", "OLD PURCHASE", "-10.00");
        api.createTransaction(user, accountId, "2026-08-15", "NEW PURCHASE", "-20.00");

        mockMvc.perform(api.get(user, "/api/transactions?from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("NEW PURCHASE"));

        Long otherAccount = api.createAccount(user, "Cash");
        mockMvc.perform(api.get(user, "/api/transactions?accountId=" + otherAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("searches description and merchant")
    void searches() throws Exception {
        api.createTransaction(user, accountId, "2026-08-01", "STARBUCKS COFFEE", "-7.20");
        api.createTransaction(user, accountId, "2026-08-02", "MCDONALDS", "-12.50");

        mockMvc.perform(api.get(user, "/api/transactions?search=starbucks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(api.get(user, "/api/transactions?search=STARB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("treats LIKE wildcards in a search term as literal text")
    void escapesLikeWildcards() throws Exception {
        api.createTransaction(user, accountId, "2026-08-01", "FIFTY PERCENT 50% OFF", "-7.20");
        api.createTransaction(user, accountId, "2026-08-02", "MCDONALDS", "-12.50");

        // Passed as a parameter rather than embedded in the URL template, so
        // MockMvc's URI encoding does not mangle the "%" before it is sent.
        // Without escaping in the query builder, "%" would match every row.
        mockMvc.perform(api.get(user, "/api/transactions").param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("is not vulnerable to SQL injection through the search term")
    void searchIsParameterised() throws Exception {
        api.createTransaction(user, accountId, "2026-08-01", "STARBUCKS", "-7.20");

        // The value is a bound parameter, so this is just a search for a string
        // nobody has - not a statement.
        mockMvc.perform(api.get(user, "/api/transactions").param("search", "x' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(stillThere).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects an inverted date range rather than silently returning nothing")
    void rejectsInvertedDateRange() throws Exception {
        mockMvc.perform(api.get(user, "/api/transactions?from=2026-08-31&to=2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("rejects a sort field that is not on the allowlist")
    void rejectsArbitrarySortField() throws Exception {
        // Passing an arbitrary property through to Spring Data would leak the
        // entity's field names through the error message at best.
        mockMvc.perform(api.get(user, "/api/transactions?sort=passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("sorts by amount when asked")
    void sortsByAmount() throws Exception {
        api.createTransaction(user, accountId, "2026-08-01", "SMALL", "-5.00");
        api.createTransaction(user, accountId, "2026-08-02", "LARGE", "-500.00");

        mockMvc.perform(api.get(user, "/api/transactions?sort=amount&direction=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("LARGE"));
    }

    @Test
    @DisplayName("stores money exactly, with no floating point drift")
    void storesMoneyExactly() throws Exception {
        api.createTransaction(user, accountId, "2026-08-01", "PRECISE", "-0.10");
        api.createTransaction(user, accountId, "2026-08-02", "PRECISE TWO", "-0.20");

        java.math.BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM transactions", java.math.BigDecimal.class);

        // 0.1 + 0.2 is famously not 0.3 in binary floating point. NUMERIC makes
        // it exactly -0.3000.
        assertThat(total).isEqualByComparingTo("-0.30");
    }
}
