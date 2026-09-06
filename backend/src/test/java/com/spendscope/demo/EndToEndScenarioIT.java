package com.spendscope.demo;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole product in one test.
 *
 * <p>Register, log in, create an account, import a statement, look at the
 * dashboard, set a budget, find the subscription, spot the unusual month,
 * customise the dashboard, log out, log back in - and find the customisation
 * still there.
 *
 * <p>Every step asserts a real number rather than just a status code, so this
 * fails if the pipeline quietly stops computing something.
 */
@DisplayName("End-to-end scenario")
class EndToEndScenarioIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("a user can go from signup to a populated, customised dashboard")
    void fullUserJourney() throws Exception {
        ApiTestClient api = new ApiTestClient(mockMvc, objectMapper);

        // ---------------------------------------------------------------
        // 1. Register and log in
        // ---------------------------------------------------------------
        TestUser user = api.registerAndLogin("journey@example.com");

        mockMvc.perform(get("/api/auth/me").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("journey@example.com"));

        // ---------------------------------------------------------------
        // 2. Create the "DBS Debit" account
        // ---------------------------------------------------------------
        Long accountId = api.createAccount(user, "DBS Debit");

        // ---------------------------------------------------------------
        // 3. Upload a statement covering four months
        // ---------------------------------------------------------------
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        // Every row is dated the 1st of its month, deliberately.
        //
        // Anomaly detection compares month-to-date against the same slice of
        // previous months. Dating rows on the 2nd or 3rd would make this test
        // pass on most days and fail whenever CI happened to run on the 1st.
        // Day 1 is inside the month-to-date window on every day of the month.
        StringBuilder csv = new StringBuilder("date,description,amount\n");
        for (int monthsBack = 3; monthsBack >= 0; monthsBack--) {
            YearMonth month = thisMonth.minusMonths(monthsBack);
            // A monthly subscription at an exact amount, for recurring detection.
            csv.append(month.atDay(1)).append(",SPOTIFY P34A9B21,-12.98\n");
            // Steady everyday spending, for a believable baseline.
            csv.append(month.atDay(1)).append(",NTUC FAIRPRICE #171,-80.00\n");
            csv.append(month.atDay(1)).append(",STARBUCKS COFFEE #4821,-7.20\n");
            if (monthsBack > 0) {
                // Modest, regular shopping, so "usual" means something.
                csv.append(month.atDay(1)).append(",UNIQLO SINGAPORE,-40.00\n");
            }
        }
        // One large purchase this month, well outside the usual range.
        csv.append(thisMonth.atDay(1)).append(",AMAZON SG MARKETPLACE LAPTOP,-689.00\n");
        // A row identical to one already in the file, for duplicate detection.
        csv.append(thisMonth.atDay(1)).append(",SPOTIFY P34A9B21,-12.98\n");

        mockMvc.perform(multipart("/api/imports/csv")
                        .file(new MockMultipartFile("file", "statement.csv", "text/csv",
                                csv.toString().getBytes(StandardCharsets.UTF_8)))
                        .param("accountId", String.valueOf(accountId))
                        .session(user.session())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(17))
                .andExpect(jsonPath("$.rowsImported").value(16))
                // 4. Duplicates are detected, not silently inserted.
                .andExpect(jsonPath("$.duplicatesDetected").value(1))
                .andExpect(jsonPath("$.rowsRejected").value(0));

        // ---------------------------------------------------------------
        // 5. Merchants were detected and categorised
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/transactions?search=spotify").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].merchant").value("Spotify"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Subscriptions"));

        // ---------------------------------------------------------------
        // 6. The dashboard summary: total, change, breakdown
        // ---------------------------------------------------------------
        // This month: 80.00 + 7.20 + 12.98 + 689.00 = 789.18
        String summary = mockMvc.perform(get("/api/analytics/summary?period=THIS_MONTH")
                        .session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(789.18))
                .andExpect(jsonPath("$.transactionCount").value(4))
                .andExpect(jsonPath("$.topCategory.label").value("Shopping"))
                .andReturn().getResponse().getContentAsString();

        // A percentage change against the same slice of last month.
        assertThat(objectMapper.readTree(summary).get("spendingChangePercentage").isNumber()).isTrue();

        // Category breakdown
        mockMvc.perform(post("/api/analytics/query")
                        .session(user.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of(
                                "metric", "CATEGORY_SPENDING",
                                "period", Map.of("type", "THIS_MONTH")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].label").value("Shopping"))
                .andExpect(jsonPath("$.points[0].value").value(689.00));

        // Monthly spending chart
        mockMvc.perform(post("/api/analytics/query")
                        .session(user.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "CUSTOM",
                                        "start", thisMonth.minusMonths(3).atDay(1).toString(),
                                        "end", today.toString()),
                                "groupBy", "MONTH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(4));

        // ---------------------------------------------------------------
        // 7. Create a Food budget and see progress
        // ---------------------------------------------------------------
        Long foodId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);

        mockMvc.perform(post("/api/budgets")
                        .session(user.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of(
                                "categoryId", foodId,
                                "month", thisMonth.toString(),
                                "amount", new BigDecimal("10.00")))))
                .andExpect(status().isCreated())
                // Starbucks 7.20 against a 10.00 budget.
                .andExpect(jsonPath("$.spent").value(7.20))
                .andExpect(jsonPath("$.percentageUsed").value(72.0))
                .andExpect(jsonPath("$.exceeded").value(false));

        // ---------------------------------------------------------------
        // 8. The recurring Spotify payment is found
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/recurring").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.merchant == 'Spotify')].cadence").value(
                        org.hamcrest.Matchers.hasItem("MONTHLY")))
                .andExpect(jsonPath("$[?(@.merchant == 'Spotify')].annualCost").value(
                        org.hamcrest.Matchers.hasItem(155.76)));

        // ---------------------------------------------------------------
        // 9. The unusual month is flagged
        // ---------------------------------------------------------------
        // Shopping ran at 40.00 a month and is suddenly 689.00. The three
        // months with no history at all count as real zeros in the baseline.
        mockMvc.perform(get("/api/insights/anomalies").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Shopping"))
                .andExpect(jsonPath("$[0].currentAmount").value(689.00))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                // Described, never accused.
                .andExpect(jsonPath("$[0].explanation").value(
                        org.hamcrest.Matchers.containsString("higher than usual")));

        // ---------------------------------------------------------------
        // 10. Customise the dashboard
        // ---------------------------------------------------------------
        int widgetsBefore = objectMapper.readTree(
                        mockMvc.perform(get("/api/dashboard/widgets").session(user.session()))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString())
                .size();

        Long newWidgetId = objectMapper.readTree(
                        mockMvc.perform(post("/api/dashboard/widgets")
                                        .session(user.session()).with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(api.json(Map.of(
                                                "metricType", "SPENDING_ANOMALIES",
                                                "period", "THIS_MONTH",
                                                "chartType", "LIST",
                                                "size", "MEDIUM",
                                                "configuration", Map.of("title", "My Unusual Spending")))))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // ---------------------------------------------------------------
        // 11. Log out, log back in - the customisation survives
        // ---------------------------------------------------------------
        mockMvc.perform(post("/api/auth/logout").session(user.session()).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dashboard/widgets").session(user.session()))
                .andExpect(status().isUnauthorized());

        MockHttpSession secondSession = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(secondSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("email", user.email(), "password", user.password()))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/widgets").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(widgetsBefore + 1))
                .andExpect(jsonPath("$[?(@.id == " + newWidgetId + ")].configuration.title").value(
                        org.hamcrest.Matchers.hasItem("My Unusual Spending")));

        // The data is still there too.
        mockMvc.perform(get("/api/analytics/summary?period=THIS_MONTH").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(789.18));
    }
}
