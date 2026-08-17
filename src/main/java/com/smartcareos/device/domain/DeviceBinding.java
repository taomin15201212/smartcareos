package com.smartcareos.device.domain;

import java.time.Instant;
import java.util.Objects;

public record DeviceBinding(
        String id,
        String tenantId,
        String deviceId,
        BindingTargetType targetType,
        String targetId,
        Instant validFrom,
        Instant validTo,
        String createdBy,
        Instant createdAt
) {
    public DeviceBinding {
        requireText(id, "id");
        requireText(tenantId, "tenantId");
        requireText(deviceId, "deviceId");
        Objects.requireNonNull(targetType, "targetType is required");
        requireText(targetId, "targetId");
        Objects.requireNonNull(validFrom, "validFrom is required");
        requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
