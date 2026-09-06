package com.spendscope.importjob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Resource limits for CSV upload, bound from {@code spendscope.import.*}.
 *
 * <p>These are the guard rails that stop one upload from consuming the whole
 * process: a bounded byte count, a bounded row count, and a bounded number of
 * error messages kept in memory while reporting.
 */
@ConfigurationProperties(prefix = "spendscope.import")
public class CsvImportProperties {

    /** Also enforced by Spring's own multipart limit; see application.yml. */
    private DataSize maxFileSize = DataSize.ofMegabytes(5);

    private int maxRows = 10_000;

    /** How many individual row errors are returned before the list is truncated. */
    private int maxReportedErrors = 50;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxReportedErrors() {
        return maxReportedErrors;
    }

    public void setMaxReportedErrors(int maxReportedErrors) {
        this.maxReportedErrors = maxReportedErrors;
    }
}
