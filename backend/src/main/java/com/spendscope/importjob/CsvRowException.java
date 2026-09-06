package com.spendscope.importjob;

/**
 * A single bad row.
 *
 * <p>Thrown per row and caught by the import loop, so one malformed line is
 * reported and skipped rather than failing the other 9,999 good ones.
 */
class CsvRowException extends RuntimeException {

    CsvRowException(String message) {
        super(message);
    }
}
