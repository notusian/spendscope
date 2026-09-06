package com.spendscope.importjob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * The record of one CSV upload.
 *
 * <p>What is <em>not</em> here is the point: the uploaded file. SpendScope
 * parses statements in memory and keeps only the transactions and these
 * counters. Storing raw bank statements would create a high-value target with
 * no feature depending on it (see docs/decisions.md).
 */
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    /** Sanitised: path separators stripped, length capped. Never used as a file path. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status;

    @Column(name = "rows_processed", nullable = false)
    private int rowsProcessed;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_rejected", nullable = false)
    private int rowsRejected;

    @Column(name = "duplicates_detected", nullable = false)
    private int duplicatesDetected;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportJob() {
        // Required by JPA.
    }

    public ImportJob(Long userId, Long accountId, String originalFilename) {
        this.userId = userId;
        this.accountId = accountId;
        this.originalFilename = originalFilename;
        this.status = ImportStatus.PENDING;
    }

    public void complete(int rowsProcessed, int rowsImported, int rowsRejected, int duplicatesDetected) {
        this.rowsProcessed = rowsProcessed;
        this.rowsImported = rowsImported;
        this.rowsRejected = rowsRejected;
        this.duplicatesDetected = duplicatesDetected;
        this.status = ImportStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        this.status = ImportStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getRowsProcessed() {
        return rowsProcessed;
    }

    public int getRowsImported() {
        return rowsImported;
    }

    public int getRowsRejected() {
        return rowsRejected;
    }

    public int getDuplicatesDetected() {
        return duplicatesDetected;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImportJob job) || id == null) {
            return false;
        }
        return id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
