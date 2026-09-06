package com.spendscope.importjob;

import com.spendscope.support.AbstractIntegrationTest;
import com.spendscope.support.ApiTestClient;
import com.spendscope.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CSV import")
class CsvImportIT extends AbstractIntegrationTest {

    private ApiTestClient api;
    private TestUser user;
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestClient(mockMvc, objectMapper);
        user = api.registerAndLogin();
        accountId = api.createAccount(user, "DBS Debit");
    }

    private ResultActions upload(String filename, String content) throws Exception {
        return upload(filename, content.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/imports/csv")
                .file(new MockMultipartFile("file", filename, "text/csv", content))
                .param("accountId", String.valueOf(accountId))
                .session(user.session())
                .with(csrf()));
    }

    @Test
    @DisplayName("imports a well formed file end to end")
    void importsValidFile() throws Exception {
        upload("statement.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                2026-08-02,GRAB,-14.50
                2026-08-03,SPOTIFY,-12.98
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(3))
                .andExpect(jsonPath("$.rowsImported").value(3))
                .andExpect(jsonPath("$.rowsRejected").value(0))
                .andExpect(jsonPath("$.duplicatesDetected").value(0));

        // The pipeline ran: merchants detected, categories assigned.
        String merchants = mockMvc.perform(api.get(user, "/api/transactions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(merchants).contains("Starbucks").contains("Grab").contains("Spotify");
        assertThat(merchants).contains("Subscriptions").contains("Transport").contains("Food");
    }

    @Test
    @DisplayName("detects duplicates on a repeat upload and imports nothing")
    void detectsDuplicates() throws Exception {
        String csv = """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                2026-08-02,GRAB,-14.50
                """;

        upload("statement.csv", csv).andExpect(jsonPath("$.rowsImported").value(2));

        upload("statement.csv", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(2))
                .andExpect(jsonPath("$.rowsImported").value(0))
                .andExpect(jsonPath("$.duplicatesDetected").value(2));

        // Reported, never deleted or overwritten: still exactly two rows.
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("imports the new rows of an overlapping statement")
    void importsOnlyNewRowsFromOverlappingFile() throws Exception {
        upload("august.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                2026-08-02,GRAB,-14.50
                """).andExpect(jsonPath("$.rowsImported").value(2));

        upload("august-september.csv", """
                date,description,amount
                2026-08-02,GRAB,-14.50
                2026-09-01,NETFLIX,-19.98
                """)
                .andExpect(jsonPath("$.rowsImported").value(1))
                .andExpect(jsonPath("$.duplicatesDetected").value(1));
    }

    @Test
    @DisplayName("de-duplicates rows repeated inside a single file")
    void deduplicatesWithinOneFile() throws Exception {
        upload("doubled.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                2026-08-01,STARBUCKS,-7.20
                """)
                .andExpect(jsonPath("$.rowsImported").value(1))
                .andExpect(jsonPath("$.duplicatesDetected").value(1));
    }

    @Test
    @DisplayName("imports the good rows of a partly broken file and reports the rest")
    void importsPartialFile() throws Exception {
        upload("messy.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                not-a-date,BROKEN ONE,-10.00
                2026-08-03,BROKEN TWO,not-a-number
                2026-08-04,,-15.00
                2026-08-05,VALID AFTER ERRORS,-22.40
                2026-08-06,ZERO AMOUNT,0.00
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(6))
                .andExpect(jsonPath("$.rowsImported").value(2))
                .andExpect(jsonPath("$.rowsRejected").value(4))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[0].line").value(3));
    }

    @Test
    @DisplayName("rejects a file whose content is not text, whatever it is called")
    void rejectsBinaryDisguisedAsCsv() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

        upload("totally-a-statement.csv", png)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FAILED"));
    }

    @Test
    @DisplayName("rejects a file with the wrong columns")
    void rejectsWrongHeaders() throws Exception {
        upload("wrong.csv", "when,what,how much\n2026-08-01,STARBUCKS,-7.20\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("date, description, amount")));
    }

    @Test
    @DisplayName("rejects an oversized file")
    void rejectsOversizedFile() throws Exception {
        // Larger than the 5 MB configured limit.
        StringBuilder csv = new StringBuilder("date,description,amount\n");
        String row = "2026-08-01,PADDING PADDING PADDING PADDING PADDING PADDING,-7.20\n";
        while (csv.length() < 6 * 1024 * 1024) {
            csv.append(row);
        }

        upload("huge.csv", csv.toString())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("records the import as a job the user can review")
    void recordsImportJob() throws Exception {
        upload("august-statement.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                """).andExpect(status().isOk());

        mockMvc.perform(api.get(user, "/api/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].originalFilename").value("august-statement.csv"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].rowsImported").value(1));
    }

    @Test
    @DisplayName("stores a traversal filename as a bare name")
    void sanitisesFilename() throws Exception {
        upload("../../../etc/passwd", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                """).andExpect(status().isOk());

        String stored = jdbcTemplate.queryForObject(
                "SELECT original_filename FROM import_jobs", String.class);
        assertThat(stored).isEqualTo("passwd").doesNotContain("..").doesNotContain("/");
    }

    @Test
    @DisplayName("does not retain the uploaded file anywhere")
    void doesNotStoreRawFile() throws Exception {
        upload("statement.csv", """
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                """).andExpect(status().isOk());

        // Only counters survive an import. There is no column anywhere holding
        // the raw statement.
        Integer columnsHoldingContent = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'import_jobs'
                  AND column_name IN ('content', 'raw_content', 'file_content', 'file_path', 'storage_path')
                """, Integer.class);
        assertThat(columnsHoldingContent).isZero();
    }

    @Test
    @DisplayName("requires a CSRF token")
    void requiresCsrfToken() throws Exception {
        mockMvc.perform(multipart("/api/imports/csv")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                "date,description,amount\n2026-08-01,X,-1.00\n".getBytes()))
                        .param("accountId", String.valueOf(accountId))
                        .session(user.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("accepts alternative date and amount formats")
    void acceptsAlternativeFormats() throws Exception {
        upload("alt.csv", """
                date,description,amount
                01/08/2026,STARBUCKS,"$7.20"
                02/08/2026,REFUND,(14.50)
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsImported").value(2));

        java.math.BigDecimal refund = jdbcTemplate.queryForObject(
                "SELECT amount FROM transactions WHERE description = 'REFUND'", java.math.BigDecimal.class);
        assertThat(refund).isEqualByComparingTo("-14.50");
    }
}
