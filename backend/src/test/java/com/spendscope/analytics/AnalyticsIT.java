package com.spendscope.analytics;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Analytics")
class AnalyticsIT extends AbstractIntegrationTest {

    private ApiTestClient api;
    private TestUser user;
    private Long accountId;

    /** First day of the current month, so THIS_MONTH always contains the data. */
    private LocalDate today;
    private LocalDate firstOfThisMonth;
    private LocalDate firstOfLastMonth;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper);
        user = api.registerAndLogin();
        accountId = api.createAccount(user, "DBS Debit");

        today = LocalDate.now();
        firstOfThisMonth = today.withDayOfMonth(1);
        firstOfLastMonth = firstOfThisMonth.minusMonths(1);
    }

    @Test
    @DisplayName("sums spending and excludes income from the spending total")
    void summarisesSpending() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-7.20");
        api.createTransaction(user, accountId, firstOfThisMonth, "FAIRPRICE", "-92.80");
        api.createTransaction(user, accountId, firstOfThisMonth, "SALARY CREDIT", "4200.00");

        mockMvc.perform(api.get(user, "/api/analytics/summary?period=THIS_MONTH"))
                .andExpect(status().isOk())
                // Spending is 100.00, not -4100: a salary is not negative spending.
                .andExpect(jsonPath("$.totalSpending").value(100.00))
                .andExpect(jsonPath("$.totalIncome").value(4200.00))
                .andExpect(jsonPath("$.netFlow").value(4100.00))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.currency").value("SGD"));
    }

    @Test
    @DisplayName("computes the change against the same slice of the previous month")
    void computesPeriodComparison() throws Exception {
        // Both dated the 1st, so the partial-month comparison window contains
        // both whatever day the suite runs on.
        api.createTransaction(user, accountId, firstOfLastMonth, "AUGUST SPEND", "-1000.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "SEPTEMBER SPEND", "-1200.00");

        mockMvc.perform(api.get(user, "/api/analytics/summary?period=THIS_MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(1200.00))
                .andExpect(jsonPath("$.previousTotalSpending").value(1000.00))
                .andExpect(jsonPath("$.spendingChangePercentage").value(20.0));
    }

    @Test
    @DisplayName("returns null rather than Infinity when there is no previous data")
    void handlesMissingComparisonData() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "FIRST EVER", "-50.00");

        String body = mockMvc.perform(api.get(user, "/api/analytics/summary?period=THIS_MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousTotalSpending").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("spendingChangePercentage").isNull()).isTrue();
        assertThat(body).doesNotContain("Infinity").doesNotContain("NaN");
    }

    @Test
    @DisplayName("reports zeroes rather than failing when a user has no data at all")
    void handlesEmptyAccount() throws Exception {
        mockMvc.perform(api.get(user, "/api/analytics/summary?period=THIS_MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(0))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.topCategory").doesNotExist());
    }

    @Test
    @DisplayName("identifies the top category and merchant")
    void identifiesTopEntries() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "FAIRPRICE GROCERIES", "-300.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-20.00");

        mockMvc.perform(api.get(user, "/api/analytics/summary?period=THIS_MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topCategory.label").value("Groceries"))
                .andExpect(jsonPath("$.topCategory.value").value(300.00))
                .andExpect(jsonPath("$.topMerchant.label").value("FairPrice"));
    }

    @Test
    @DisplayName("computes the average daily spend over the period length")
    void computesAverageDaily() throws Exception {
        api.createTransaction(user, accountId, today, "TODAY", "-100.00");

        // LAST_30_DAYS is always 30 days, so the average is deterministic.
        mockMvc.perform(api.get(user, "/api/analytics/summary?period=LAST_30_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDailySpending").value(3.3333))
                .andExpect(jsonPath("$.period.days").value(30));
    }

    @Test
    @DisplayName("groups by category with each slice's share of the total")
    void groupsByCategory() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "FAIRPRICE", "-75.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-25.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "CATEGORY_SPENDING",
                                "period", Map.of("type", "THIS_MONTH"),
                                "comparison", "PREVIOUS_PERIOD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("CATEGORY"))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.points[0].label").value("Groceries"))
                .andExpect(jsonPath("$.points[0].value").value(75.00))
                .andExpect(jsonPath("$.points[0].share").value(75.0))
                .andExpect(jsonPath("$.points[1].label").value("Food"))
                .andExpect(jsonPath("$.points[1].share").value(25.0));
    }

    @Test
    @DisplayName("groups by merchant and honours the limit")
    void groupsByMerchantWithLimit() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "FAIRPRICE", "-75.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-50.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "UNIQLO", "-25.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "THIS_MONTH"),
                                "groupBy", "MERCHANT",
                                "limit", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].label").value("FairPrice"));
    }

    @Test
    @DisplayName("returns dense time buckets so a quiet day is a zero, not a gap")
    void returnsDenseTimeBuckets() throws Exception {
        api.createTransaction(user, accountId, today.minusDays(5), "OLDER", "-10.00");
        api.createTransaction(user, accountId, today, "TODAY", "-20.00");

        String body = mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "LAST_30_DAYS"),
                                "groupBy", "DAY"))))
                .andExpect(status().isOk())
                // 30 days in, 30 buckets out - a line chart must not connect
                // across missing days as though they never happened. This also
                // guards the result limit, which must never truncate a series.
                .andExpect(jsonPath("$.points.length()").value(30))
                .andReturn().getResponse().getContentAsString();

        // Every bucket carries a real number: a quiet day is an explicit zero,
        // never a null the chart would have to guess about.
        var points = objectMapper.readTree(body).get("points");
        for (var point : points) {
            assertThat(point.get("value").isNumber())
                    .as("bucket %s has a numeric value", point.get("key").asText())
                    .isTrue();
        }
        assertThat(points.get(0).get("value").decimalValue()).isEqualByComparingTo("0");
        assertThat(points.get(29).get("value").decimalValue()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("groups by month")
    void groupsByMonth() throws Exception {
        api.createTransaction(user, accountId, firstOfLastMonth, "LAST MONTH", "-100.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "THIS MONTH", "-200.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "CUSTOM",
                                        "start", firstOfLastMonth.toString(),
                                        "end", today.toString()),
                                "groupBy", "MONTH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].value").value(100.00))
                .andExpect(jsonPath("$.points[1].value").value(200.00));
    }

    @Test
    @DisplayName("counts transactions as a scalar metric")
    void countsTransactions() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "ONE", "-10.00");
        api.createTransaction(user, accountId, firstOfThisMonth, "TWO", "-20.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TRANSACTION_COUNT",
                                "period", Map.of("type", "THIS_MONTH")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @ParameterizedTest
    @EnumSource(value = GroupBy.class, names = {"DAY", "WEEK", "MONTH"})
    void comparesCorrespondingTimeBuckets(GroupBy grouping) throws Exception {
        api.createTransaction(user, accountId, firstOfLastMonth, "CURRENT PERIOD", "-30.00");
        api.createTransaction(user, accountId, firstOfLastMonth.minusMonths(1), "PREVIOUS PERIOD", "-12.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "LAST_MONTH"),
                                "groupBy", grouping.name(),
                                "comparison", "PREVIOUS_PERIOD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousTotal").value(12.00))
                .andExpect(jsonPath("$.points[0].value").value(30.00))
                .andExpect(jsonPath("$.points[0].previousValue").value(12.00))
                .andExpect(jsonPath("$.points[0].percentageChange").value(150.00));
    }

    @ParameterizedTest
    @EnumSource(value = Metric.class, names = {"TRANSACTION_COUNT", "AVERAGE_DAILY_SPENDING"})
    void rejectsUnsupportedGroupingInsteadOfReturningSpending(Metric metric) throws Exception {
        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", metric.name(),
                                "period", Map.of("type", "THIS_MONTH"),
                                "groupBy", "DAY"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesNestedPeriodFields() throws Exception {
        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of("metric", "TOTAL_SPENDING", "period", Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['period.type']").exists());
    }

    @Test
    @DisplayName("requires explicit dates for a custom period")
    void rejectsCustomPeriodWithoutDates() throws Exception {
        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "TOTAL_SPENDING",
                                "period", Map.of("type", "CUSTOM")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects an unknown metric without leaking internals")
    void rejectsUnknownMetric() throws Exception {
        String body = mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content("{\"metric\":\"DROP_TABLE\",\"period\":{\"type\":\"THIS_MONTH\"}}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("com.spendscope")
                .doesNotContain("Exception")
                .doesNotContain("at java.");
    }

    @Test
    @DisplayName("shows uncategorised spending as its own bucket rather than hiding it")
    void reportsUncategorisedSpending() throws Exception {
        api.createTransaction(user, accountId, firstOfThisMonth, "SOME LOCAL KOPITIAM 88", "-12.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "CATEGORY_SPENDING",
                                "period", Map.of("type", "THIS_MONTH")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].label").value("Uncategorised"))
                .andExpect(jsonPath("$.points[0].value").value(12.00));
    }

    @Test
    @DisplayName("groups by account")
    void groupsByAccount() throws Exception {
        Long cashId = api.createAccount(user, "Cash");
        api.createTransaction(user, accountId, firstOfThisMonth, "CARD SPEND", "-80.00");
        api.createTransaction(user, cashId, firstOfThisMonth, "CASH SPEND", "-20.00");

        mockMvc.perform(api.post(user, "/api/analytics/query")
                        .content(api.json(Map.of(
                                "metric", "ACCOUNT_SPENDING",
                                "period", Map.of("type", "THIS_MONTH")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].label").value("DBS Debit"))
                .andExpect(jsonPath("$.points[0].value").value(80.00));
    }

    @Test
    @DisplayName("budget progress is derived from transactions, not stored")
    void budgetProgressReflectsTransactions() throws Exception {
        Long foodId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);

        mockMvc.perform(api.post(user, "/api/budgets")
                        .content(api.json(Map.of(
                                "categoryId", foodId,
                                "month", YearMonth.from(today).toString(),
                                "amount", 500))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spent").value(0));

        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-420.00");

        // No update call was made to the budget. The number moves because it is
        // computed, which is the whole point of not storing it.
        mockMvc.perform(api.get(user, "/api/budgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spent").value(420.00))
                .andExpect(jsonPath("$[0].remaining").value(80.00))
                .andExpect(jsonPath("$[0].percentageUsed").value(84.0))
                .andExpect(jsonPath("$[0].exceeded").value(false));
    }

    @Test
    @DisplayName("an exceeded budget reports a negative remaining")
    void reportsExceededBudget() throws Exception {
        Long foodId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);

        mockMvc.perform(api.post(user, "/api/budgets")
                        .content(api.json(Map.of(
                                "categoryId", foodId,
                                "month", YearMonth.from(today).toString(),
                                "amount", 100))))
                .andExpect(status().isCreated());

        api.createTransaction(user, accountId, firstOfThisMonth, "STARBUCKS", "-150.00");

        mockMvc.perform(api.get(user, "/api/budgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remaining").value(-50.00))
                .andExpect(jsonPath("$[0].exceeded").value(true))
                .andExpect(jsonPath("$[0].percentageUsed").value(150.0));
    }
}
