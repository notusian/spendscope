package com.spendscope.auth;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Authentication")
class AuthenticationIT extends AbstractIntegrationTest {

    private ApiTestClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiTestClient(mockMvc, objectMapper);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authRequest(
            String path, Object body) throws Exception {
        return post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(api.json(body));
    }

    @Test
    @DisplayName("registers a new account and returns safe fields only")
    void registersUser() throws Exception {
        String response = mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "new@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andReturn().getResponse().getContentAsString();

        // The response must never carry credential material. A DTO makes this
        // structural, and this assertion keeps it that way.
        assertThat(response)
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("JSESSIONID");
    }

    @Test
    @DisplayName("normalises the email so case and whitespace do not create a second account")
    void normalisesEmail() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "  Mixed.Case@Example.COM ", "password", "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mixed.case@example.com"));

        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "mixed.case@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("rejects a duplicate email")
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "dupe@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isCreated());

        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "dupe@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rejects an invalid email and reports the offending field")
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "not-an-email", "password", "correct-horse-battery")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("rejects a short password")
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "short@example.com", "password", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("rejects a password longer than BCrypt can actually hash")
    void rejectsOverlongPassword() throws Exception {
        // Validation must reject the input before it reaches BCrypt.
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "long@example.com", "password", "x".repeat(100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("validates Unicode passwords against the encoded byte limit")
    void validatesUnicodePasswordBytes() throws Exception {
        String password = "界".repeat(24);
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "unicode@example.com", "password", password)))
                .andExpect(status().isCreated());
        mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", "unicode@example.com", "password", password)))
                .andExpect(status().isOk());
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "oversized@example.com", "password", password + "a")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("stores an adaptive hash, never the password")
    void storesBcryptHash() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "hash@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isCreated());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "hash@example.com");

        assertThat(storedHash)
                .isNotNull()
                .doesNotContain("correct-horse-battery")
                // Delegating encoder prefix, which is what allows the algorithm
                // to be upgraded later.
                .startsWith("{bcrypt}$2a$");
    }

    @Test
    @DisplayName("logs in and establishes a session")
    void logsIn() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "login@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isCreated());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("email", "login@example.com",
                                "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    @DisplayName("gives the same answer for a wrong password and an unknown email")
    void doesNotRevealWhetherAnAccountExists() throws Exception {
        mockMvc.perform(authRequest("/api/auth/register",
                        Map.of("email", "known@example.com", "password", "correct-horse-battery")))
                .andExpect(status().isCreated());

        String wrongPassword = mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", "known@example.com", "password", "wrong-password-here")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", "nobody@example.com", "password", "wrong-password-here")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical messages: login must not be usable to enumerate accounts.
        assertThat(objectMapper.readTree(wrongPassword).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknownEmail).get("message").asText())
                .isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("logging out invalidates the session")
    void logsOut() throws Exception {
        TestUser user = api.registerAndLogin("logout@example.com");

        mockMvc.perform(get("/api/auth/me").session(user.session()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(user.session()).with(csrf()))
                .andExpect(status().isNoContent());

        // The old session must no longer authenticate anything.
        mockMvc.perform(get("/api/auth/me").session(user.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a new user gets a starter dashboard")
    void newUserGetsDefaultWidgets() throws Exception {
        TestUser user = api.registerAndLogin("widgets@example.com");

        mockMvc.perform(get("/api/dashboard/widgets").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    @DisplayName("changing a password requires the current one")
    void changePasswordRequiresCurrent() throws Exception {
        TestUser user = api.registerAndLogin("changepw@example.com");

        mockMvc.perform(api.post(user, "/api/auth/password")
                        .content(api.json(Map.of(
                                "currentPassword", "definitely-not-it",
                                "newPassword", "another-good-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(api.post(user, "/api/auth/password")
                        .content(api.json(Map.of(
                                "currentPassword", user.password(),
                                "newPassword", "another-good-password"))))
                .andExpect(status().isNoContent());

        // The new password works and the old one does not.
        mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", user.email(), "password", "another-good-password")))
                .andExpect(status().isOk());
        mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", user.email(), "password", user.password())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("records login and registration in the audit trail")
    void writesAuditEvents() throws Exception {
        TestUser user = api.registerAndLogin("audited@example.com");

        String body = mockMvc.perform(get("/api/security/events").session(user.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("USER_REGISTERED").contains("LOGIN_SUCCESS");
        // And never anything credential-like.
        assertThat(body).doesNotContain("password").doesNotContain("JSESSIONID");
    }

    @Test
    @DisplayName("records a failed login attempt")
    void recordsFailedLogin() throws Exception {
        TestUser user = api.registerAndLogin("failed@example.com");

        mockMvc.perform(authRequest("/api/auth/login",
                        Map.of("email", user.email(), "password", "wrong-password-here")))
                .andExpect(status().isUnauthorized());

        Integer failures = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE user_id = ? AND event_type = 'LOGIN_FAILURE'",
                Integer.class, user.id());
        assertThat(failures).isEqualTo(1);
    }
}
