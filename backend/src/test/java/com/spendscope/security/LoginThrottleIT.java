package com.spendscope.security;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login throttling, in its own context with a deliberately tiny limit.
 *
 * <p>The shared test profile raises the limit to 1000 so that the rest of the
 * suite - which logs in constantly - is never throttled. Testing the throttle
 * against that limit would mean a thousand BCrypt verifications per run, so
 * this class overrides it to three instead.
 */
@TestPropertySource(properties = {
        "spendscope.security.max-login-attempts=3",
        "spendscope.security.login-attempt-window=15m"
})
@DisplayName("Login throttling")
class LoginThrottleIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("blocks with 429 after too many failed attempts")
    void blocksAfterRepeatedFailures() throws Exception {
        ApiTestClient api = new ApiTestClient(mockMvc, objectMapper);
        TestUser user = api.registerAndLogin("throttle@example.com");

        String wrongLogin = api.json(Map.of("email", user.email(), "password", "definitely-wrong"));

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                    .andExpect(status().isUnauthorized());
        }

        // 429 rather than 401, so the client can tell the user to wait rather
        // than insisting their password is wrong.
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Even the correct password is refused while the block is in force.
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("email", user.email(), "password", user.password()))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("records the block in the audit trail")
    void recordsBlockedAttempt() throws Exception {
        ApiTestClient api = new ApiTestClient(mockMvc, objectMapper);
        TestUser user = api.registerAndLogin("blocked@example.com");
        String wrongLogin = api.json(Map.of("email", user.email(), "password", "definitely-wrong"));

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(wrongLogin));
        }

        Integer blocked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = 'LOGIN_BLOCKED'", Integer.class);
        assertThat(blocked).isGreaterThanOrEqualTo(1);
    }
}
