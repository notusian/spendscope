package com.spendscope.importjob;

import com.spendscope.common.error.ImportRejectedException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns uploaded bytes into validated rows.
 *
 * <p>Expected format:
 * <pre>
 * date,description,amount
 * 2026-08-01,STARBUCKS,-7.20
 * </pre>
 *
 * <p>Safety properties, in order of how much they matter:
 * <ol>
 *   <li><b>The content is validated, not the filename.</b> A file called
 *       {@code statement.csv} that contains a JPEG is rejected, and a file
 *       called {@code statement.txt} that contains valid CSV is accepted.</li>
 *   <li><b>Rows are bounded.</b> Parsing stops at the configured maximum so a
 *       crafted file cannot exhaust memory.</li>
 *   <li><b>One bad row is one bad row.</b> Errors are collected per line; the
 *       valid rows around them still import.</li>
 * </ol>
 */
@Component
public class CsvTransactionParser {

    private static final String DATE_HEADER = "date";
    private static final String DESCRIPTION_HEADER = "description";
    private static final String AMOUNT_HEADER = "amount";
    private static final Set<String> REQUIRED_HEADERS = Set.of(DATE_HEADER, DESCRIPTION_HEADER, AMOUNT_HEADER);

    /** UTF-8 byte order mark, which Excel writes and CSV parsers do not expect. */
    private static final char BYTE_ORDER_MARK = '﻿';

    /** How much of the file is inspected when deciding whether it is text at all. */
    private static final int CONTENT_SNIFF_LENGTH = 8192;

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final CsvImportProperties properties;

    public CsvTransactionParser(CsvImportProperties properties) {
        this.properties = properties;
    }

    /**
     * @param rows             every row that parsed and validated
     * @param errors           per-line messages for rows that did not
     * @param rowsProcessed    total data rows seen, valid or not
     * @param errorsTruncated  true when more rows failed than are listed
     */
    public record ParseResult(List<CsvRow> rows,
                              List<ImportSummaryResponse.RowError> errors,
                              int rowsProcessed,
                              boolean errorsTruncated) {
    }

    public ParseResult parse(byte[] content) {
        String text = decodeAsText(content);

        List<CsvRow> rows = new ArrayList<>();
        List<ImportSummaryResponse.RowError> errors = new ArrayList<>();
        int rowsProcessed = 0;
        boolean errorsTruncated = false;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        try (CSVParser parser = CSVParser.parse(text, format)) {
            requireHeaders(parser);

            for (CSVRecord record : parser) {
                if (rowsProcessed >= properties.getMaxRows()) {
                    throw ImportRejectedException.tooLarge(
                            "The file has more than " + properties.getMaxRows() + " rows");
                }
                rowsProcessed++;
                try {
                    rows.add(toRow(record));
                } catch (CsvRowException ex) {
                    if (errors.size() < properties.getMaxReportedErrors()) {
                        errors.add(new ImportSummaryResponse.RowError(record.getRecordNumber() + 1, ex.getMessage()));
                    } else {
                        errorsTruncated = true;
                    }
                }
            }
        } catch (IOException | UncheckedIOException ex) {
            // Commons CSV reports structural problems (an unterminated quote,
            // for example) as IOException.
            throw ImportRejectedException.invalid("The file could not be parsed as CSV");
        } catch (IllegalArgumentException ex) {
            throw ImportRejectedException.invalid("The file could not be parsed as CSV");
        }

        return new ParseResult(rows, errors, rowsProcessed, errorsTruncated);
    }

    private void requireHeaders(CSVParser parser) {
        Set<String> headers = parser.getHeaderMap().keySet().stream()
                .map(header -> header == null ? "" : header.trim().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!headers.containsAll(REQUIRED_HEADERS)) {
            throw ImportRejectedException.invalid(
                    "The file must have a header row with columns: date, description, amount");
        }
    }

    private CsvRow toRow(CSVRecord record) {
        String description = valueOf(record, DESCRIPTION_HEADER);
        if (description.isBlank()) {
            throw new CsvRowException("Description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return new CsvRow(
                record.getRecordNumber() + 1,
                CsvValueParsers.parseDate(valueOf(record, DATE_HEADER)),
                description,
                CsvValueParsers.parseAmount(valueOf(record, AMOUNT_HEADER)));
    }

    /** A short row is a bad row, not a crash. */
    private String valueOf(CSVRecord record, String header) {
        if (!record.isMapped(header) || !record.isSet(header)) {
            throw new CsvRowException("Missing '" + header + "' column");
        }
        String value = record.get(header);
        return value == null ? "" : value;
    }

    /**
     * Decodes the upload as UTF-8, rejecting anything that is not text.
     *
     * <p>This is the content check that the filename extension cannot give:
     * strict UTF-8 decoding fails on binary, and an explicit NUL byte scan
     * catches UTF-16 and similar files that would otherwise decode into
     * garbage.
     */
    private String decodeAsText(byte[] content) {
        if (content.length == 0) {
            throw ImportRejectedException.invalid("The file is empty");
        }
        if (content.length > properties.getMaxFileSize().toBytes()) {
            throw ImportRejectedException.tooLarge(
                    "The file is larger than " + properties.getMaxFileSize().toMegabytes() + " MB");
        }

        int sniffLength = Math.min(content.length, CONTENT_SNIFF_LENGTH);
        for (int index = 0; index < sniffLength; index++) {
            if (content[index] == 0) {
                throw ImportRejectedException.invalid("The file does not look like a text CSV file");
            }
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
            text = decoded.toString();
        } catch (CharacterCodingException ex) {
            throw ImportRejectedException.invalid("The file must be UTF-8 encoded text");
        }

        if (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
            text = text.substring(1);
        }
        return text;
    }
}
