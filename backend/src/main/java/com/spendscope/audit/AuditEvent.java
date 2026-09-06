package com.spendscope.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * One recorded security relevant event.
 *
 * <p>{@code userId} is a plain column rather than a {@code @ManyToOne}: audit
 * rows are written on paths where the user may not exist (a failed login for an
 * unknown email), and they are only ever read back as a flat list.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * Small, non-sensitive context such as {@code {"accountId": 12}}.
     * {@link AuditService} strips anything credential-like before it gets here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    protected AuditEvent() {
        // Required by JPA.
    }

    public AuditEvent(Long userId, AuditEventType eventType, Map<String, Object> metadata) {
        this.userId = userId;
        this.eventType = eventType;
        this.metadata = metadata == null ? Map.of() : metadata;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
