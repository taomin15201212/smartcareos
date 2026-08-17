package com.smartcareos.messaging.domain;

import java.time.Instant;
import java.util.Objects;

public record OutboxMessage(
        String eventId,
        String tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String payload,
        int attemptCount
) {
    public OutboxMessage {
        requireText(eventId, "eventId");
        requireText(tenantId, "tenantId");
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        requireText(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        requireText(payload, "payload");
        if (schemaVersion < 1 || attemptCount < 1) {
            throw new IllegalArgumentException("schemaVersion and attemptCount must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
