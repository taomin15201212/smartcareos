package com.smartcareos.device.application;

import com.smartcareos.device.domain.Device;
import com.smartcareos.device.domain.DeviceStatus;

import java.time.Instant;

public record DeviceSnapshot(
        String id,
        String tenantId,
        String deviceKey,
        String productId,
        DeviceStatus status,
        long version,
        Instant registeredAt
) {
    static DeviceSnapshot from(Device device) {
        return new DeviceSnapshot(
                device.id(), device.tenantId(), device.deviceKey(), device.productId(),
                device.status(), device.version(), device.registeredAt());
    }
}

