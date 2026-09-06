package com.spendscope.importjob;

import java.util.List;

/**
 * The result of an import.
 *
 * <pre>
 * {
 *   "rowsProcessed": 150,
 *   "rowsImported": 143,
 *   "rowsRejected": 4,
 *   "duplicatesDetected": 3
 * }
 * </pre>
 *
 * <p>{@code rowsProcessed = rowsImported + rowsRejected + duplicatesDetected}.
 * Duplicates are counted separately from rejections because they are not
 * errors: the row was understood, it just already exists.
 */
public record ImportSummaryResponse(
        Long importJobId,
        int rowsProcessed,
        int rowsImported,
        int rowsRejected,
        int duplicatesDetected,
        List<RowError> errors,
        boolean errorsTruncated) {

    public record RowError(long line, String message) {
    }
}
