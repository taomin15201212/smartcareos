package com.smartcareos.device.application;

import com.smartcareos.device.domain.BindingTargetType;
import com.smartcareos.device.domain.Device;
import com.smartcareos.device.domain.DeviceBinding;
import com.smartcareos.elder.application.ElderDirectory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DeviceRegistryService {

    private final DeviceRegistryStore store;
    private final ElderDirectory elderDirectory;
    private final Clock clock;

    public DeviceRegistryService(
            DeviceRegistryStore store, ElderDirectory elderDirectory, Clock clock
    ) {
        this.store = store;
        this.elderDirectory = elderDirectory;
        this.clock = clock;
    }

    public DeviceRegistryStore.DeviceProductSnapshot createProduct(
            String tenantId, String productKey, String name
    ) {
        return store.createProduct(new DeviceRegistryStore.DeviceProductSnapshot(
                UUID.randomUUID().toString(), tenantId, productKey, name, "ACTIVE", clock.instant()));
    }

    public DeviceSnapshot register(
            String tenantId, String deviceKey, String productId, String actorId
    ) {
        Device device = Device.register(
                UUID.randomUUID().toString(), tenantId, deviceKey, productId, clock.instant());
        return DeviceSnapshot.from(store.register(device, actorId));
    }

    public DeviceSnapshot get(String deviceId) {
        return DeviceSnapshot.from(store.findDevice(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId)));
    }

    public DeviceSnapshot activate(String deviceId, String actorId) {
        Device device = load(deviceId);
        device.activate();
        return DeviceSnapshot.from(store.changeStatus(device, actorId, clock.instant()));
    }

    public DeviceSnapshot disable(String deviceId, String actorId) {
        Device device = load(deviceId);
        device.disable();
        return DeviceSnapshot.from(store.changeStatus(device, actorId, clock.instant()));
    }

    public DeviceBinding bindElder(
            String deviceId,
            String elderId,
            Instant validFrom,
            Instant validTo,
            String createdBy
    ) {
        Device device = load(deviceId);
        elderDirectory.requireActiveElder(device.tenantId(), elderId);
        return store.createBinding(new DeviceBinding(
                UUID.randomUUID().toString(), device.tenantId(), device.id(),
                BindingTargetType.ELDER, elderId, validFrom, validTo, createdBy, clock.instant()));
    }

    public DeviceBinding closeBinding(String deviceId, String bindingId, Instant validTo) {
        return store.closeBinding(deviceId, bindingId, validTo);
    }

    public void validateRiskEvent(
            String tenantId, String deviceKey, String elderId, Instant observedAt
    ) {
        DeviceRegistryStore.DeviceEligibility eligibility =
                store.eligibilityAt(tenantId, deviceKey, elderId, observedAt);
        if (!eligibility.eligible()) {
            throw new DeviceEventRejectedException(eligibility.rejectionReason());
        }
    }

    private Device load(String deviceId) {
        return store.findDevice(deviceId).orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }
}
