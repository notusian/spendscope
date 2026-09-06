package com.spendscope.importjob;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Page<ImportJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
