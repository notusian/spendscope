package com.spendscope.importjob;

import com.spendscope.common.error.ErrorCode;
import com.spendscope.common.error.ImportRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTransactionParserTest {

    private CsvImportProperties properties;
    private CsvTransactionParser parser;

    @BeforeEach
    void setUp() {
        properties = new CsvImportProperties();
        properties.setMaxRows(1000);
        properties.setMaxFileSize(DataSize.ofKilobytes(64));
        properties.setMaxReportedErrors(3);
        parser = new CsvTransactionParser(properties);
    }

    private CsvTransactionParser.ParseResult parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("parses the documented format")
    void parsesValidCsv() {
        CsvTransactionParser.ParseResult result = parse("""
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                2026-08-02,GRAB,-14.50
                2026-08-03,SPOTIFY,-12.98
                """);

        assertThat(result.rowsProcessed()).isEqualTo(3);
        assertThat(result.rows()).hasSize(3);
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.rows().get(0).description()).isEqualTo("STARBUCKS");
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("-7.20");
    }

    @Test
    @DisplayName("one malformed row does not reject the valid rows around it")
    void malformedRowsAreIsolated() {
        CsvTransactionParser.ParseResult result = parse("""
                date,description,amount
                2026-08-01,STARBUCKS,-7.20
                not-a-date,BROKEN,-10.00
                2026-08-03,VALID AFTER ERROR,-22.40
                """);

        assertThat(result.rowsProcessed()).isEqualTo(3);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3);
        assertThat(result.errors().get(0).message()).contains("not-a-date");
    }

    @Test
    @DisplayName("handles quoted fields containing commas")
    void handlesQuotedFields() {
        // This is why a real CSV parser is used instead of String.split(",").
        CsvTransactionParser.ParseResult result = parse("""
                date,description,amount
                2026-08-01,"ACME, INC. PAYMENT",-7.20
                """);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).isEqualTo("ACME, INC. PAYMENT");
    }

    @Test
    @DisplayName("accepts headers in any case and any order")
    void headersAreCaseAndOrderInsensitive() {
        CsvTransactionParser.ParseResult result = parse("""
                Amount,DATE,Description
                -7.20,2026-08-01,STARBUCKS
                """);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).isEqualTo("STARBUCKS");
    }

    @Test
    @DisplayName("strips the byte order mark Excel writes")
    void stripsByteOrderMark() {
        CsvTransactionParser.ParseResult result =
                parse("﻿date,description,amount\n2026-08-01,STARBUCKS,-7.20\n");
        assertThat(result.rows()).hasSize(1);
    }

    @Test
    @DisplayName("rejects a file with the wrong headers")
    void rejectsWrongHeaders() {
        assertThatThrownBy(() -> parse("when,what,how much\n2026-08-01,STARBUCKS,-7.20\n"))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("date, description, amount");
    }

    @Test
    @DisplayName("rejects an empty file")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("rejects binary content whatever the filename said")
    void rejectsBinaryContent() {
        // The filename extension is not trusted; the content decides. A NUL
        // byte is not valid in a text CSV.
        byte[] pngHeader = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        assertThatThrownBy(() -> parser.parse(pngHeader))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("does not look like a text CSV");
    }

    @Test
    void rejectsNulBeyondTheFirstEightKilobytes() {
        String csv = "date,description,amount\n"
                + "2026-08-01,VALID ROW,-7.20\n".repeat(400)
                + "2026-08-02,INVALID\0DESCRIPTION,-10.00\n";
        assertThatThrownBy(() -> parse(csv)).isInstanceOf(ImportRejectedException.class);
    }

    @Test
    void rejectsAmbiguousDuplicateHeaders() {
        assertThatThrownBy(() -> parse("date,description,amount,AMOUNT\n2026-08-01,SHOP,-7.20,-720\n"))
                .isInstanceOf(ImportRejectedException.class);
    }

    @Test
    void isolatesInvalidDatesAndRoundedAmounts() {
        CsvTransactionParser.ParseResult result = parse("""
                date,description,amount
                31/04/2026,INVALID DATE,-10.00
                2026-08-01,ROUNDS TO ZERO,0.00001
                2026-08-02,ROUNDS TOO LARGE,999999999999999.99995
                2026-08-03,VALID,-22.40
                """);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.errors()).hasSize(3);
        assertThat(result.rowsProcessed()).isEqualTo(4);
    }

    @Test
    @DisplayName("rejects invalid UTF-8")
    void rejectsInvalidUtf8() {
        byte[] invalid = "date,description,amount\n2026-08-01,".getBytes(StandardCharsets.UTF_8);
        byte[] withBadByte = new byte[invalid.length + 3];
        System.arraycopy(invalid, 0, withBadByte, 0, invalid.length);
        withBadByte[invalid.length] = (byte) 0xFF;
        withBadByte[invalid.length + 1] = (byte) 0xFE;
        withBadByte[invalid.length + 2] = (byte) 0xFD;

        assertThatThrownBy(() -> parser.parse(withBadByte))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    @DisplayName("rejects a file over the configured size")
    void rejectsOversizedFile() {
        properties.setMaxFileSize(DataSize.ofBytes(50));
        String csv = "date,description,amount\n" + "2026-08-01,STARBUCKS,-7.20\n".repeat(10);

        assertThatThrownBy(() -> parse(csv))
                .isInstanceOf(ImportRejectedException.class)
                .satisfies(thrown -> assertThat(((ImportRejectedException) thrown).getCode())
                        .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    @DisplayName("rejects a file with too many rows")
    void rejectsTooManyRows() {
        properties.setMaxRows(5);
        String csv = "date,description,amount\n" + "2026-08-01,STARBUCKS,-7.20\n".repeat(20);

        assertThatThrownBy(() -> parse(csv))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("more than 5 rows");
    }

    @Test
    @DisplayName("caps how many row errors it reports and says that it did")
    void truncatesErrorList() {
        String csv = "date,description,amount\n" + "bad-date,BROKEN,-1.00\n".repeat(10);
        CsvTransactionParser.ParseResult result = parse(csv);

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).hasSize(3);
        assertThat(result.errorsTruncated()).isTrue();
    }

    @Test
    @DisplayName("skips blank lines rather than reporting them as errors")
    void skipsBlankLines() {
        CsvTransactionParser.ParseResult result = parse("""
                date,description,amount
                2026-08-01,STARBUCKS,-7.20

                2026-08-02,GRAB,-14.50
                """);

        assertThat(result.rowsProcessed()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("truncates an over-long description instead of failing the row")
    void truncatesLongDescription() {
        CsvTransactionParser.ParseResult result =
                parse("date,description,amount\n2026-08-01," + "A".repeat(900) + ",-7.20\n");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).hasSize(500);
    }
}
