package com.spendscope.demo;

import com.spendscope.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo endpoint is the one place an anonymous caller can create rows, so it
 * is rate limited per IP. This runs in its own context with a small limit.
 */
@TestPropertySource(properties = "spendscope.demo.max-sessions-per-ip=2")
@DisplayName("Demo rate limiting")
class DemoThrottleIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("stops one address from creating unlimited demo accounts")
    void limitsDemoSessionsPerAddress() throws Exception {
        mockMvc.perform(post("/api/demo/session").with(csrf())).andExpect(status().isCreated());
        mockMvc.perform(post("/api/demo/session").with(csrf())).andExpect(status().isCreated());

        // Each demo account is three accounts and several hundred rows. Without
        // a limit this endpoint is a free write amplifier for anyone.
        mockMvc.perform(post("/api/demo/session").with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
