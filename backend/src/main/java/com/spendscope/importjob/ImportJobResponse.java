package com.spendscope.importjob;

import java.time.Instant;

public record ImportJobResponse(
        Long id,
        Long accountId,
        String originalFilename,
        ImportStatus status,
        int rowsProcessed,
        int rowsImported,
        int rowsRejected,
        int duplicatesDetected,
        Instant createdAt,
        Instant completedAt) {

    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getAccountId(),
                job.getOriginalFilename(),
                job.getStatus(),
                job.getRowsProcessed(),
                job.getRowsImported(),
                job.getRowsRejected(),
                job.getDuplicatesDetected(),
                job.getCreatedAt(),
                job.getCompletedAt());
    }
}
