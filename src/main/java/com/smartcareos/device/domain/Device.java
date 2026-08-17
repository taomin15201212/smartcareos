package com.smartcareos.device.domain;

import java.time.Instant;
import java.util.Objects;

public final class Device {

    private final String id;
    private final String tenantId;
    private final String deviceKey;
    private final String productId;
    private DeviceStatus status;
    private long version;
    private final Instant registeredAt;

    private Device(
            String id,
            String tenantId,
            String deviceKey,
            String productId,
            DeviceStatus status,
            long version,
            Instant registeredAt
    ) {
        this.id = required(id, "id");
        this.tenantId = required(tenantId, "tenantId");
        this.deviceKey = required(deviceKey, "deviceKey");
        this.productId = required(productId, "productId");
        this.status = Objects.requireNonNull(status, "status is required");
        this.version = version;
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt is required");
    }

    public static Device register(
            String id, String tenantId, String deviceKey, String productId, Instant registeredAt
    ) {
        return new Device(id, tenantId, deviceKey, productId,
                DeviceStatus.REGISTERED, 0, registeredAt);
    }

    public static Device restore(
            String id,
            String tenantId,
            String deviceKey,
            String productId,
            DeviceStatus status,
            long version,
            Instant registeredAt
    ) {
        return new Device(id, tenantId, deviceKey, productId, status, version, registeredAt);
    }

    public DeviceStatus activate() {
        if (status != DeviceStatus.REGISTERED) {
            throw new DeviceDomainException("only a registered device can be activated");
        }
        DeviceStatus previous = status;
        status = DeviceStatus.ACTIVE;
        version++;
        return previous;
    }

    public DeviceStatus disable() {
        if (status != DeviceStatus.ACTIVE) {
            throw new DeviceDomainException("only an active device can be disabled");
        }
        DeviceStatus previous = status;
        status = DeviceStatus.DISABLED;
        version++;
        return previous;
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String deviceKey() { return deviceKey; }
    public String productId() { return productId; }
    public DeviceStatus status() { return status; }
    public long version() { return version; }
    public Instant registeredAt() { return registeredAt; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

