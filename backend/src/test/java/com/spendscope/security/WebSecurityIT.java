package com.spendscope.security;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF, CORS, cookies, headers and error hygiene.
 */
@DisplayName("Web security")
class WebSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private ServerProperties serverProperties;

    private ApiTestClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiTestClient(mockMvc, objectMapper);
    }

    @Test
    @DisplayName("rejects a state-changing request with no CSRF token")
    void rejectsMissingCsrfToken() throws Exception {
        TestUser user = api.registerAndLogin();

        // Authenticated, but no token: exactly the shape of a cross-site POST
        // riding the victim's cookie.
        mockMvc.perform(post("/api/accounts")
                        .session(user.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Forged", "type", "CASH", "currency", "SGD"))))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE name = 'Forged'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("rejects an invalid CSRF token")
    void rejectsInvalidCsrfToken() throws Exception {
        TestUser user = api.registerAndLogin();

        mockMvc.perform(post("/api/accounts")
                        .session(user.session())
                        .header("X-XSRF-TOKEN", "not-the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("name", "Forged", "type", "CASH", "currency", "SGD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("does not require a CSRF token for safe methods")
    void allowsSafeMethodsWithoutToken() throws Exception {
        TestUser user = api.registerAndLogin();

        mockMvc.perform(get("/api/accounts").session(user.session()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the session cookie is HttpOnly and SameSite-restricted")
    void sessionCookieIsHardened() {
        // Asserted against the bound configuration rather than a response
        // header: MockMvc does not run the servlet container's cookie writer,
        // but this is the exact object the container reads at runtime.
        //
        // HttpOnly is the important one. If JavaScript could read the session
        // id, any XSS would escalate from a page-scoped problem to full account
        // takeover.
        var cookie = serverProperties.getServlet().getSession().getCookie();

        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isNotNull();
        assertThat(serverProperties.getServlet().getSession().getTimeout()).isNotNull();
    }

    @Test
    @DisplayName("allows the configured frontend origin")
    void allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/accounts")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("rejects an origin that is not on the list")
    void rejectsUnknownOrigin() throws Exception {
        // A wildcard here would let any page on the internet drive this API
        // using the victim's session cookie.
        mockMvc.perform(options("/api/accounts")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("never returns a wildcard allow-origin")
    void neverReturnsWildcardOrigin() throws Exception {
        var response = mockMvc.perform(options("/api/accounts")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNotEqualTo("*");
    }

    @Test
    @DisplayName("sends the expected security headers")
    void sendsSecurityHeaders() throws Exception {
        TestUser user = api.registerAndLogin();

        mockMvc.perform(get("/api/accounts").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
    }

    @Test
    @DisplayName("does not offer a Basic auth prompt")
    void doesNotOfferBasicAuth() throws Exception {
        var response = mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        // A WWW-Authenticate header would make the browser show a native login
        // dialog the SPA cannot control.
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @DisplayName("never leaks a stack trace or internal detail in an error")
    void errorsDoNotLeakInternals() throws Exception {
        TestUser user = api.registerAndLogin();

        String notFound = mockMvc.perform(get("/api/transactions/999999").session(user.session()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String badJson = mockMvc.perform(post("/api/transactions")
                        .session(user.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[]{notFound, badJson}) {
            assertThat(body)
                    .doesNotContain("com.spendscope")
                    .doesNotContain("org.springframework")
                    .doesNotContain("org.hibernate")
                    .doesNotContain("Exception")
                    .doesNotContain("at java.")
                    .doesNotContain("SELECT")
                    .doesNotContain("jdbc");
        }
    }

    @Test
    @DisplayName("every error uses the same response shape")
    void errorsShareOneShape() throws Exception {
        String body = mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        var error = objectMapper.readTree(body);
        assertThat(error.has("timestamp")).isTrue();
        assertThat(error.has("status")).isTrue();
        assertThat(error.has("code")).isTrue();
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("path")).isTrue();
    }

    @Test
    @DisplayName("the health endpoint is public but reveals no detail")
    void healthEndpointIsSafe() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("UP");
        // No component breakdown: db vendor, disk paths and connection details
        // are operational information, not public information.
        assertThat(body)
                .doesNotContain("PostgreSQL")
                .doesNotContain("jdbc")
                .doesNotContain("diskSpace");
    }

    @Test
    @DisplayName("sensitive actuator endpoints are not exposed")
    void sensitiveActuatorEndpointsAreClosed() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/mappings"}) {
            mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    @DisplayName("a stale session no longer authenticates after logout")
    void staleSessionIsRejected() throws Exception {
        TestUser user = api.registerAndLogin();
        MockHttpSession session = user.session();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").session(session))
                .andExpect(status().isUnauthorized());
    }

}
