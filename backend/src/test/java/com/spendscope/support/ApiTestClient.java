package com.spendscope.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Convenience wrapper over MockMvc for the integration tests.
 *
 * <p>Keeps the tests readable: a test about cross-user access should be three
 * lines about cross-user access, not thirty lines of request building.
 */
public class ApiTestClient {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Registers a user and logs them in, returning the authenticated session. */
    public TestUser registerAndLogin() throws Exception {
        return registerAndLogin("user" + UNIQUE.incrementAndGet() + "@example.com");
    }

    public TestUser registerAndLogin(String email) throws Exception {
        String password = "correct-horse-battery-staple";

        MvcResult registration = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = read(registration).get("id").asLong();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());

        return new TestUser(userId, email, password, session);
    }

    public Long createAccount(TestUser user, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(user, "/api/accounts")
                        .content(json(Map.of("name", name, "type", "CHECKING", "currency", "SGD"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    public Long createTransaction(TestUser user, Long accountId, String date, String description,
                                  String amount) throws Exception {
        MvcResult result = mockMvc.perform(post(user, "/api/transactions")
                        .content(json(Map.of(
                                "accountId", accountId,
                                "date", date,
                                "description", description,
                                "amount", new BigDecimal(amount)))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    public Long createTransaction(TestUser user, Long accountId, LocalDate date, String description,
                                  String amount) throws Exception {
        return createTransaction(user, accountId, date.toString(), description, amount);
    }

    /** Authenticated GET. */
    public MockHttpServletRequestBuilder get(TestUser user, String path) {
        return MockMvcRequestBuilders.get(path).session(user.session());
    }

    /** Authenticated POST with a JSON body and a valid CSRF token. */
    public MockHttpServletRequestBuilder post(TestUser user, String path) {
        return MockMvcRequestBuilders.post(path)
                .session(user.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }

    public MockHttpServletRequestBuilder put(TestUser user, String path) {
        return MockMvcRequestBuilders.put(path)
                .session(user.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }

    public MockHttpServletRequestBuilder patch(TestUser user, String path) {
        return MockMvcRequestBuilders.patch(path)
                .session(user.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }

    public MockHttpServletRequestBuilder delete(TestUser user, String path) {
        return MockMvcRequestBuilders.delete(path).session(user.session()).with(csrf());
    }

    public String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    public JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
