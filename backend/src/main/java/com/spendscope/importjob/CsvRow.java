package com.spendscope.importjob;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One validated row from the uploaded file.
 *
 * @param lineNumber the line in the original file, so an error message can
 *                   point the user at the right place
 */
record CsvRow(long lineNumber, LocalDate date, String description, BigDecimal amount) {
}
