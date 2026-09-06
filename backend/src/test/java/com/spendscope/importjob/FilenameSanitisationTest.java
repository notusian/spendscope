package com.spendscope.importjob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The uploaded filename is attacker-controlled text that is stored and later
 * rendered. Nothing opens a file by this name, but it still gets scrubbed.
 */
class FilenameSanitisationTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "statement.csv                       | statement.csv",
            "../../../etc/passwd                 | passwd",
            "..\\..\\windows\\system32\\config   | config",
            "/absolute/path/statement.csv        | statement.csv",
            "C:\\Users\\bob\\statement.csv       | statement.csv",
    })
    @DisplayName("strips directory components whatever separator was used")
    void stripsPaths(String input, String expected) {
        assertThat(CsvImportService.sanitiseFilename(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("replaces traversal-only names with a safe default")
    void replacesTraversalNames() {
        assertThat(CsvImportService.sanitiseFilename("..")).isEqualTo("upload.csv");
        assertThat(CsvImportService.sanitiseFilename(".")).isEqualTo("upload.csv");
        assertThat(CsvImportService.sanitiseFilename("../")).isEqualTo("upload.csv");
    }

    @Test
    @DisplayName("removes control characters")
    void removesControlCharacters() {
        assertThat(CsvImportService.sanitiseFilename("state\u0000ment\n.csv")).isEqualTo("statement.csv");
    }

    @Test
    @DisplayName("falls back to a default for missing or blank names")
    void handlesMissingName() {
        assertThat(CsvImportService.sanitiseFilename(null)).isEqualTo("upload.csv");
        assertThat(CsvImportService.sanitiseFilename("   ")).isEqualTo("upload.csv");
    }

    @Test
    @DisplayName("caps the length to the column width")
    void capsLength() {
        assertThat(CsvImportService.sanitiseFilename("a".repeat(500))).hasSize(255);
    }
}
