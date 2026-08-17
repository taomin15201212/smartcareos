package com.smartcareos.device.application;

import com.smartcareos.device.domain.Device;
import com.smartcareos.device.domain.DeviceBinding;

import java.time.Instant;
import java.util.Optional;

public interface DeviceRegistryStore {

    DeviceProductSnapshot createProduct(DeviceProductSnapshot product);

    Device register(Device device, String actorId);

    Optional<Device> findDevice(String deviceId);

    Device changeStatus(Device device, String actorId, Instant occurredAt);

    DeviceBinding createBinding(DeviceBinding binding);

    DeviceBinding closeBinding(String deviceId, String bindingId, Instant validTo);

    DeviceEligibility eligibilityAt(
            String tenantId, String deviceKey, String elderId, Instant observedAt
    );

    record DeviceProductSnapshot(
            String id,
            String tenantId,
            String productKey,
            String name,
            String status,
            Instant createdAt
    ) {
    }

    record DeviceEligibility(boolean eligible, String rejectionReason) {
        public static DeviceEligibility accepted() {
            return new DeviceEligibility(true, null);
        }

        public static DeviceEligibility rejected(String reason) {
            return new DeviceEligibility(false, reason);
        }
    }
}
