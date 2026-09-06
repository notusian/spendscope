package com.spendscope.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit trail is readable by the user, so anything credential-like that
 * reaches it is a disclosure. These tests pin that filter down.
 */
class AuditMetadataTest {

    @Test
    @DisplayName("keeps ordinary context")
    void keepsSafeKeys() {
        Map<String, Object> safe = AuditService.sanitise(Map.of("accountId", 12L));
        assertThat(safe).containsEntry("accountId", 12L);
    }

    @Test
    @DisplayName("drops anything that looks like a credential")
    void dropsCredentialKeys() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("password", "hunter2");
        input.put("userPassword", "hunter2");
        input.put("sessionId", "ABC123");
        input.put("csrfToken", "tok");
        input.put("Authorization", "Bearer x");
        input.put("passwordHash", "$2a$10$...");
        input.put("apiKey", "sk-live-123");
        input.put("cookie", "JSESSIONID=x");
        input.put("accountId", 7L);

        Map<String, Object> safe = AuditService.sanitise(input);

        assertThat(safe).containsExactly(Map.entry("accountId", 7L));
    }

    @Test
    @DisplayName("matches credential fragments case-insensitively and inside longer names")
    void matchesFragmentsAnywhere() {
        assertThat(AuditService.sanitise(Map.of("MY_SECRET_VALUE", "x"))).isEmpty();
        assertThat(AuditService.sanitise(Map.of("refreshTokenValue", "x"))).isEmpty();
    }

    @Test
    @DisplayName("truncates long values so one call cannot bloat the table")
    void truncatesLongValues() {
        Map<String, Object> safe = AuditService.sanitise(Map.of("reason", "x".repeat(1000)));
        assertThat((String) safe.get("reason")).hasSize(200);
    }

    @Test
    @DisplayName("drops nulls and handles an empty map")
    void handlesEmptyAndNulls() {
        assertThat(AuditService.sanitise(null)).isEmpty();
        assertThat(AuditService.sanitise(Map.of())).isEmpty();

        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("accountId", null);
        assertThat(AuditService.sanitise(withNull)).isEmpty();
    }
}
