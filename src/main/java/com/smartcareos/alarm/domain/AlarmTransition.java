package com.smartcareos.alarm.domain;

import java.time.Instant;
import java.util.Objects;

public record AlarmTransition(
        AlarmStatus from,
        AlarmStatus to,
        String action,
        String actorId,
        Instant occurredAt
) {
    public AlarmTransition {
        Objects.requireNonNull(to, "to must not be null");
        requireText(action, "action");
        requireText(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
