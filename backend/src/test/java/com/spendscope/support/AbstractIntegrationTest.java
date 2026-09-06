package com.spendscope.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL.
 *
 * <p><b>Why not H2.</b> Half of what these tests exist to verify does not exist
 * in H2: {@code date_trunc}, JSONB columns, partial unique indexes, the
 * {@code EXTRACT(DAY FROM ...)} filter behind anomaly detection. A test suite
 * that passes on H2 and fails in production is worse than no suite.
 *
 * <p><b>Where the database comes from.</b> Testcontainers by default, which is
 * what CI uses. When Docker is not available - a developer laptop without it,
 * for instance - setting {@code SPENDSCOPE_TEST_DB_URL} (plus
 * {@code ..._USERNAME} / {@code ..._PASSWORD}) points the suite at an existing
 * PostgreSQL instead. The tests are identical either way; only the source of
 * the connection differs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String DEFAULT_IMAGE = "postgres:17-alpine";

    private static final String EXTERNAL_URL = System.getenv("SPENDSCOPE_TEST_DB_URL");
    private static final PostgreSQLContainer<?> CONTAINER;

    static {
        if (EXTERNAL_URL == null) {
            CONTAINER = new PostgreSQLContainer<>(DEFAULT_IMAGE)
                    .withDatabaseName("spendscope_test")
                    .withUsername("spendscope")
                    .withPassword("spendscope")
                    // Reused across every test class in the run; JVM shutdown
                    // stops it. Starting one container per class would add
                    // minutes to the suite for no extra isolation, because the
                    // data is wiped before each test anyway.
                    .withReuse(false);
            CONTAINER.start();
        } else {
            CONTAINER = null;
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username",
                    () -> envOrDefault("SPENDSCOPE_TEST_DB_USERNAME", "spendscope"));
            registry.add("spring.datasource.password",
                    () -> envOrDefault("SPENDSCOPE_TEST_DB_PASSWORD", "spendscope"));
        } else {
            registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", CONTAINER::getUsername);
            registry.add("spring.datasource.password", CONTAINER::getPassword);
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Wipes user data before each test.
     *
     * <p>Deleting from {@code users} is enough: every other user-owned table
     * hangs off it with ON DELETE CASCADE, which is both convenient here and a
     * genuine assertion that those cascades are configured. The built-in
     * categories seeded by V3 have a null user_id and therefore survive, which
     * is exactly the production shape.
     */
    @BeforeEach
    void resetDatabase() {
        // JdbcTemplate rather than an EntityManager: this has to commit on its
        // own, outside any test transaction, so the state is genuinely gone
        // before the test's own HTTP requests run.
        jdbcTemplate.update("DELETE FROM users");
    }
}
