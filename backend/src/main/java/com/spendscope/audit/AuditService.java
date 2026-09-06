package com.spendscope.audit;

import com.spendscope.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes and reads the audit trail.
 *
 * <p>Two deliberate properties:
 * <ol>
 *   <li><b>Metadata is filtered.</b> A denylist of credential-like key
 *       fragments is dropped before persisting, so a careless caller cannot
 *       leak a password or session id into a table the user can read back.</li>
 *   <li><b>Writing an audit row never breaks the business operation.</b>
 *       {@code REQUIRES_NEW} plus a caught exception means a failure to audit
 *       is logged, not propagated - losing a log line is better than failing a
 *       user's transaction import.</li>
 * </ol>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /**
     * Key fragments that must never be persisted. Matched case-insensitively
     * as substrings, so "userPassword" and "csrfToken" are both caught.
     */
    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "password", "secret", "token", "cookie", "session", "authorization",
            "credential", "hash", "csrf", "apikey", "api_key");

    /** Guards against an accidentally huge metadata blob. */
    private static final int MAX_VALUE_LENGTH = 200;

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(Long userId, AuditEventType eventType) {
        record(userId, eventType, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, AuditEventType eventType, Map<String, Object> metadata) {
        try {
            auditEventRepository.save(new AuditEvent(userId, eventType, sanitise(metadata)));
        } catch (RuntimeException ex) {
            log.error("Failed to record audit event {} for user {}", eventType, userId, ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> listForUser(Long userId, int page, int size) {
        Page<AuditEvent> events =
                auditEventRepository.findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(events, AuditEventResponse::from);
    }

    /**
     * Drops credential-like keys and truncates long values.
     *
     * <p>Package-private so it can be unit tested directly.
     */
    static Map<String, Object> sanitise(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || isForbidden(key) || value == null) {
                return;
            }
            safe.put(key, truncate(value));
        });
        return safe;
    }

    private static boolean isForbidden(String key) {
        String lowerCaseKey = key.toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(lowerCaseKey::contains);
    }

    private static Object truncate(Object value) {
        if (value instanceof String text && text.length() > MAX_VALUE_LENGTH) {
            return text.substring(0, MAX_VALUE_LENGTH);
        }
        return value;
    }
}
