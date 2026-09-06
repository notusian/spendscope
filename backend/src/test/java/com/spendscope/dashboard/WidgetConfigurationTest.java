package com.spendscope.dashboard;

import com.spendscope.common.error.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The widget configuration map goes into a JSONB column as supplied, so it is
 * allowlisted rather than trusted.
 */
class WidgetConfigurationTest {

    @Test
    @DisplayName("keeps the known keys")
    void keepsAllowedKeys() {
        Map<String, Object> safe = DashboardService.sanitiseConfiguration(
                Map.of("title", "Top Merchants", "limit", 6, "showLegend", true));

        assertThat(safe)
                .containsEntry("title", "Top Merchants")
                .containsEntry("limit", 6)
                .containsEntry("showLegend", true);
    }

    @Test
    @DisplayName("drops unknown keys instead of storing whatever a client sends")
    void dropsUnknownKeys() {
        Map<String, Object> safe = DashboardService.sanitiseConfiguration(
                Map.of("title", "Fine", "arbitraryPayload", "junk"));

        assertThat(safe).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("drops nested objects and arrays")
    void dropsNestedStructures() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", "Fine");
        input.put("limit", List.of(1, 2, 3));
        input.put("categoryId", Map.of("nested", "value"));

        assertThat(DashboardService.sanitiseConfiguration(input)).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("rejects a configuration with too many entries")
    void rejectsTooManyEntries() {
        Map<String, Object> input = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            input.put("key" + index, index);
        }
        assertThatThrownBy(() -> DashboardService.sanitiseConfiguration(input))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("rejects an over-long string value")
    void rejectsLongStrings() {
        assertThatThrownBy(() -> DashboardService.sanitiseConfiguration(Map.of("title", "x".repeat(500))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("short strings");
    }

    @Test
    @DisplayName("handles null and empty input")
    void handlesEmpty() {
        assertThat(DashboardService.sanitiseConfiguration(null)).isEmpty();
        assertThat(DashboardService.sanitiseConfiguration(Map.of())).isEmpty();
    }
}
