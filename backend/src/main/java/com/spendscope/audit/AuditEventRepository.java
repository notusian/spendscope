package com.spendscope.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Ownership-scoped by construction: there is no method to read another
     * user's audit trail.
     */
    Page<AuditEvent> findByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);
}
