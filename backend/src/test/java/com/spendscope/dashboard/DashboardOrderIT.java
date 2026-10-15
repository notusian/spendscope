package com.spendscope.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardOrderIT extends AbstractIntegrationTest {
    private ApiTestClient api;
    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper);
        user = api.registerAndLogin();
    }

    @Test
    void rejectsDuplicateIdsWithoutChangingTheStoredOrder() throws Exception {
        List<Long> original = widgetIds();
        List<Long> duplicates = new ArrayList<>(original);
        duplicates.set(1, duplicates.getFirst());

        mockMvc.perform(api.put(user, "/api/dashboard/widgets/order")
                        .content(api.json(Map.of("widgetIds", duplicates))))
                .andExpect(status().isBadRequest());

        assertThat(widgetIds()).containsExactlyElementsOf(original);
    }

    @Test
    void persistsACompletePermutationOfTheWidgets() throws Exception {
        List<Long> reversed = new ArrayList<>(widgetIds());
        Collections.reverse(reversed);

        mockMvc.perform(api.put(user, "/api/dashboard/widgets/order")
                        .content(api.json(Map.of("widgetIds", reversed))))
                .andExpect(status().isOk());

        assertThat(widgetIds()).containsExactlyElementsOf(reversed);
    }

    private List<Long> widgetIds() throws Exception {
        String json = mockMvc.perform(api.get(user, "/api/dashboard/widgets"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode widget : objectMapper.readTree(json)) {
            ids.add(widget.get("id").longValue());
        }
        return ids;
    }
}
