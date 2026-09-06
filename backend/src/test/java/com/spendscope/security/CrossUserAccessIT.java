package com.spendscope.security;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorisation test suite. These are the tests that matter most.
 *
 * <p>Ids in this application are sequential integers, so guessing another
 * user's transaction id is trivial. The only thing standing between Bob and
 * Alice's data is that every query is scoped by the authenticated user id -
 * and that is what is asserted here, endpoint by endpoint.
 *
 * <p>Note the expected status: <b>404, not 403</b>. Returning "forbidden" for a
 * row that exists and "not found" for one that does not would let an attacker
 * enumerate which ids are real. Both answers are identical.
 */
@DisplayName("Cross-user access")
class CrossUserAccessIT extends AbstractIntegrationTest {

    private ApiTestClient api;
    private TestUser alice;
    private TestUser bob;
    private Long aliceAccountId;
    private Long aliceTransactionId;
    private Long aliceBudgetId;
    private Long aliceWidgetId;
    private Long aliceCategoryId;

    @BeforeEach
    void setUpUsers() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper);
        alice = api.registerAndLogin("alice@example.com");
        bob = api.registerAndLogin("bob@example.com");

        aliceAccountId = api.createAccount(alice, "Alice DBS");
        aliceTransactionId = api.createTransaction(
                alice, aliceAccountId, LocalDate.now().withDayOfMonth(1), "ALICE SECRET COFFEE", "-7.20");

        aliceCategoryId = api.read(mockMvc.perform(api.post(alice, "/api/categories")
                        .content(api.json(Map.of("name", "Alice Private", "type", "EXPENSE"))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        aliceBudgetId = api.read(mockMvc.perform(api.post(alice, "/api/budgets")
                        .content(api.json(Map.of(
                                "categoryId", aliceCategoryId,
                                "month", LocalDate.now().toString().substring(0, 7),
                                "amount", 500))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        aliceWidgetId = api.read(mockMvc.perform(api.get(alice, "/api/dashboard/widgets"))
                .andExpect(status().isOk()).andReturn()).get(0).get("id").asLong();
    }

    @Nested
    @DisplayName("Anonymous callers")
    class Anonymous {

        @Test
        @DisplayName("cannot reach any private endpoint")
        void anonymousIsRejectedEverywhere() throws Exception {
            String[] privatePaths = {
                    "/api/auth/me",
                    "/api/accounts",
                    "/api/transactions",
                    "/api/categories",
                    "/api/budgets",
                    "/api/dashboard/widgets",
                    "/api/analytics/summary",
                    "/api/recurring",
                    "/api/insights/anomalies",
                    "/api/imports",
                    "/api/security/events",
            };

            for (String path : privatePaths) {
                mockMvc.perform(get(path))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            }
        }

        @Test
        @DisplayName("cannot read a specific resource by id")
        void anonymousCannotReadById() throws Exception {
            mockMvc.perform(get("/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/accounts/" + aliceAccountId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("cannot write without a CSRF token")
        void anonymousCannotPostWithoutCsrf() throws Exception {
            // No session and no CSRF token: rejected before any handler runs.
            mockMvc.perform(post("/api/transactions").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Alice")
    class Owner {

        @Test
        @DisplayName("can read her own transaction")
        void ownerCanRead() throws Exception {
            mockMvc.perform(api.get(alice, "/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("ALICE SECRET COFFEE"));
        }

        @Test
        @DisplayName("can read her own account")
        void ownerCanReadAccount() throws Exception {
            mockMvc.perform(api.get(alice, "/api/accounts/" + aliceAccountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice DBS"));
        }
    }

    @Nested
    @DisplayName("Bob")
    class Intruder {

        @Test
        @DisplayName("cannot read Alice's transaction")
        void cannotReadTransaction() throws Exception {
            mockMvc.perform(api.get(bob, "/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("cannot modify Alice's transaction")
        void cannotModifyTransaction() throws Exception {
            Long bobAccountId = api.createAccount(bob, "Bob OCBC");

            mockMvc.perform(api.put(bob, "/api/transactions/" + aliceTransactionId)
                            .content(api.json(Map.of(
                                    "accountId", bobAccountId,
                                    "date", "2026-01-01",
                                    "description", "HIJACKED",
                                    "amount", -1))))
                    .andExpect(status().isNotFound());

            // And the original is untouched.
            mockMvc.perform(api.get(alice, "/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("ALICE SECRET COFFEE"));
        }

        @Test
        @DisplayName("cannot re-categorise Alice's transaction")
        void cannotRecategoriseTransaction() throws Exception {
            mockMvc.perform(api.patch(bob, "/api/transactions/" + aliceTransactionId + "/category")
                            .content("{\"categoryId\":null}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot delete Alice's transaction")
        void cannotDeleteTransaction() throws Exception {
            mockMvc.perform(api.delete(bob, "/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isNotFound());

            mockMvc.perform(api.get(alice, "/api/transactions/" + aliceTransactionId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("cannot read, modify, archive or delete Alice's account")
        void cannotTouchAccount() throws Exception {
            mockMvc.perform(api.get(bob, "/api/accounts/" + aliceAccountId))
                    .andExpect(status().isNotFound());
            mockMvc.perform(api.put(bob, "/api/accounts/" + aliceAccountId)
                            .content(api.json(Map.of("name", "Stolen", "type", "CASH", "currency", "USD"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(api.post(bob, "/api/accounts/" + aliceAccountId + "/archive"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(api.delete(bob, "/api/accounts/" + aliceAccountId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot attach his own transaction to Alice's account")
        void cannotWriteIntoAnotherUsersAccount() throws Exception {
            // The account id is validated against the caller before the
            // transaction is created, so this cannot be used to inject rows
            // into someone else's history.
            mockMvc.perform(api.post(bob, "/api/transactions")
                            .content(api.json(Map.of(
                                    "accountId", aliceAccountId,
                                    "date", "2026-01-01",
                                    "description", "INJECTED",
                                    "amount", -5))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot import a CSV into Alice's account")
        void cannotImportIntoAnotherUsersAccount() throws Exception {
            var file = new org.springframework.mock.web.MockMultipartFile(
                    "file", "statement.csv", "text/csv",
                    "date,description,amount\n2026-08-01,INJECTED,-9.99\n".getBytes());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/imports/csv")
                            .file(file)
                            .param("accountId", String.valueOf(aliceAccountId))
                            .session(bob.session())
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot use Alice's private category")
        void cannotUseAnotherUsersCategory() throws Exception {
            Long bobAccountId = api.createAccount(bob, "Bob Cash");

            mockMvc.perform(api.post(bob, "/api/transactions")
                            .content(api.json(Map.of(
                                    "accountId", bobAccountId,
                                    "categoryId", aliceCategoryId,
                                    "date", "2026-01-01",
                                    "description", "MISLABELLED",
                                    "amount", -5))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot delete Alice's custom category")
        void cannotDeleteAnotherUsersCategory() throws Exception {
            mockMvc.perform(api.delete(bob, "/api/categories/" + aliceCategoryId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot see Alice's custom category in his own list")
        void cannotListAnotherUsersCategories() throws Exception {
            String body = mockMvc.perform(api.get(bob, "/api/categories"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("Alice Private");
        }

        @Test
        @DisplayName("cannot read, modify or delete Alice's budget")
        void cannotTouchBudget() throws Exception {
            mockMvc.perform(api.put(bob, "/api/budgets/" + aliceBudgetId)
                            .content(api.json(Map.of(
                                    "categoryId", aliceCategoryId,
                                    "month", LocalDate.now().toString().substring(0, 7),
                                    "amount", 1))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(api.delete(bob, "/api/budgets/" + aliceBudgetId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot modify or delete Alice's dashboard widget")
        void cannotTouchWidget() throws Exception {
            mockMvc.perform(api.put(bob, "/api/dashboard/widgets/" + aliceWidgetId)
                            .content(api.json(Map.of(
                                    "metricType", "TOTAL_SPENDING",
                                    "period", "THIS_MONTH",
                                    "chartType", "NUMBER",
                                    "size", "SMALL"))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(api.delete(bob, "/api/dashboard/widgets/" + aliceWidgetId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("cannot reorder using Alice's widget ids")
        void cannotReorderAnotherUsersWidgets() throws Exception {
            mockMvc.perform(api.put(bob, "/api/dashboard/widgets/order")
                            .content(api.json(Map.of("widgetIds", java.util.List.of(aliceWidgetId)))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Analytics isolation")
    class AnalyticsIsolation {

        @Test
        @DisplayName("Bob's analytics contain only Bob's data")
        void analyticsAreScopedToTheCaller() throws Exception {
            Long bobAccountId = api.createAccount(bob, "Bob OCBC");
            api.createTransaction(bob, bobAccountId, LocalDate.now().withDayOfMonth(1), "BOB LUNCH", "-11.00");

            // Alice: 7.20. Bob: 11.00. Neither should ever see the other's number.
            mockMvc.perform(api.get(bob, "/api/analytics/summary?period=THIS_MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(11.00))
                    .andExpect(jsonPath("$.transactionCount").value(1));

            mockMvc.perform(api.get(alice, "/api/analytics/summary?period=THIS_MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(7.20))
                    .andExpect(jsonPath("$.transactionCount").value(1));
        }

        @Test
        @DisplayName("a grouped analytics query never leaks another user's merchants")
        void groupedQueriesAreScoped() throws Exception {
            Long bobAccountId = api.createAccount(bob, "Bob OCBC");
            api.createTransaction(bob, bobAccountId, LocalDate.now().withDayOfMonth(1), "BOB LUNCH", "-11.00");

            String body = mockMvc.perform(api.post(bob, "/api/analytics/query")
                            .content(api.json(Map.of(
                                    "metric", "TOTAL_SPENDING",
                                    "period", Map.of("type", "THIS_YEAR"),
                                    "groupBy", "MERCHANT"))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("Bob Lunch").doesNotContain("Alice Secret Coffee");
        }

        @Test
        @DisplayName("Bob's transaction list never contains Alice's rows")
        void listIsScoped() throws Exception {
            String body = mockMvc.perform(api.get(bob, "/api/transactions?size=200"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("ALICE SECRET COFFEE");
        }

        @Test
        @DisplayName("Bob's security events never contain Alice's")
        void auditTrailIsScoped() throws Exception {
            String body = mockMvc.perform(api.get(bob, "/api/security/events?size=100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("alice@example.com");
        }

        @Test
        @DisplayName("Bob's import history never contains Alice's")
        void importHistoryIsScoped() throws Exception {
            var file = new org.springframework.mock.web.MockMultipartFile(
                    "file", "alice-statement.csv", "text/csv",
                    "date,description,amount\n2026-08-01,ALICE IMPORT,-9.99\n".getBytes());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/imports/csv")
                            .file(file)
                            .param("accountId", String.valueOf(aliceAccountId))
                            .session(alice.session())
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isOk());

            mockMvc.perform(api.get(bob, "/api/imports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
