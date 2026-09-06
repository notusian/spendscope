package com.spendscope.demo;

import com.spendscope.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Demo mode")
class DemoIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("creates a populated demo account and logs the visitor straight in")
    void createsDemoSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/demo/session").session(session).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.email").value(
                        org.hamcrest.Matchers.endsWith("@demo.spendscope.local")));

        // Already authenticated: no separate login step.
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true));
    }

    @Test
    @DisplayName("the demo has enough data to make every feature meaningful")
    void demoDataExercisesEveryFeature() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/demo/session").session(session).with(csrf()))
                .andExpect(status().isCreated());

        // Accounts
        mockMvc.perform(get("/api/accounts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Several hundred transactions across nine months
        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(transactionCount).isGreaterThan(300);

        // A dashboard
        mockMvc.perform(get("/api/dashboard/widgets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));

        // Budgets, some of which are exceeded
        mockMvc.perform(get("/api/budgets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        // Analytics that are not all zero
        String summary = mockMvc.perform(get("/api/analytics/summary?period=LAST_90_DAYS").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(summary).get("totalSpending").decimalValue())
                .isGreaterThan(java.math.BigDecimal.ZERO);

        // Recurring subscriptions, including the exact-amount ones
        String recurring = mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(recurring).contains("Spotify").contains("Netflix").contains("MONTHLY");

        // At least one unusual-spending insight
        mockMvc.perform(get("/api/insights/anomalies").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("demo data is never dated in the future")
    void demoDataHasNoFutureDates() throws Exception {
        mockMvc.perform(post("/api/demo/session").with(csrf())).andExpect(status().isCreated());

        Integer futureRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE date > CURRENT_DATE", Integer.class);
        assertThat(futureRows).isZero();
    }

    @Test
    @DisplayName("two demo sessions get separate accounts and cannot see each other")
    void demoSessionsAreIsolated() throws Exception {
        MockHttpSession first = new MockHttpSession();
        MockHttpSession second = new MockHttpSession();

        mockMvc.perform(post("/api/demo/session").session(first).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/demo/session").session(second).with(csrf()))
                .andExpect(status().isCreated());

        Integer users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_demo", Integer.class);
        assertThat(users).isEqualTo(2);

        // Each sees only its own three accounts, not six.
        mockMvc.perform(get("/api/accounts").session(first))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/accounts").session(second))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("demo accounts are flagged so they can be reaped")
    void demoAccountsAreFlagged() throws Exception {
        mockMvc.perform(post("/api/demo/session").with(csrf())).andExpect(status().isCreated());

        Boolean isDemo = jdbcTemplate.queryForObject(
                "SELECT is_demo FROM users LIMIT 1", Boolean.class);
        assertThat(isDemo).isTrue();
    }
}
