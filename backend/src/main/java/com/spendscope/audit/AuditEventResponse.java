package com.spendscope.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(Long id, AuditEventType eventType, Instant occurredAt,
                                 Map<String, Object> metadata) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getEventType(), event.getOccurredAt(),
                event.getMetadata());
    }
}
