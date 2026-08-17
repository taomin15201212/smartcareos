package com.smartcareos.device.domain;

import com.smartcareos.alarm.domain.AlarmSeverity;

import java.time.Instant;
import java.util.Objects;

public record RiskSourceEvent(
        String eventId,
        String tenantId,
        String deviceId,
        String elderId,
        AlarmSeverity severity,
        Instant observedAt
) {
    public RiskSourceEvent {
        eventId = requireText(eventId, 128, "eventId");
        tenantId = requireText(tenantId, 64, "tenantId");
        deviceId = requireText(deviceId, 64, "deviceId");
        elderId = requireText(elderId, 64, "elderId");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    private static String requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }
}
