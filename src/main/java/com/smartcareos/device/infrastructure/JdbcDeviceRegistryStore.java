package com.smartcareos.device.infrastructure;

import com.smartcareos.device.application.DeviceConflictException;
import com.smartcareos.device.application.DeviceNotFoundException;
import com.smartcareos.device.application.DeviceRegistryStore;
import com.smartcareos.device.domain.BindingTargetType;
import com.smartcareos.device.domain.Device;
import com.smartcareos.device.domain.DeviceBinding;
import com.smartcareos.device.domain.DeviceStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDeviceRegistryStore implements DeviceRegistryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDeviceRegistryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public DeviceProductSnapshot createProduct(DeviceProductSnapshot product) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO device_product (
                                id, tenant_id, product_key, name, status, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    product.id(), product.tenantId(), product.productKey(), product.name(),
                    product.status(), timestamp(product.createdAt()));
            return product;
        } catch (DuplicateKeyException exception) {
            throw new DeviceConflictException(
                    "device product key already exists in tenant: " + product.productKey(), exception);
        }
    }

    @Override
    @Transactional
    public Device register(Device device, String actorId) {
        Integer matchingProduct = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM device_product
                         WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
                        """, Integer.class, device.productId(), device.tenantId());
        if (matchingProduct == null || matchingProduct != 1) {
            throw new DeviceConflictException("active device product does not belong to tenant");
        }
        try {
            jdbcTemplate.update("""
                            INSERT INTO device (
                                id, tenant_id, device_key, product_id, status, version, registered_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    device.id(), device.tenantId(), device.deviceKey(), device.productId(),
                    device.status().name(), device.version(), timestamp(device.registeredAt()));
            insertHistory(device.id(), 0, null, device.status(), actorId, device.registeredAt());
            return device;
        } catch (DuplicateKeyException exception) {
            throw new DeviceConflictException(
                    "device key already exists in tenant: " + device.deviceKey(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findDevice(String deviceId) {
        List<Device> devices = jdbcTemplate.query("""
                        SELECT id, tenant_id, device_key, product_id, status, version, registered_at
                          FROM device WHERE id = ?
                        """, this::mapDevice, deviceId);
        return devices.stream().findFirst();
    }

    @Override
    @Transactional
    public Device changeStatus(Device device, String actorId, Instant occurredAt) {
        long previousVersion = device.version() - 1;
        DeviceStatus previousStatus = jdbcTemplate.query("""
                        SELECT status FROM device WHERE id = ?
                        """, resultSet -> resultSet.next()
                        ? DeviceStatus.valueOf(resultSet.getString(1)) : null, device.id());
        if (previousStatus == null) {
            throw new DeviceNotFoundException(device.id());
        }
        int updated = jdbcTemplate.update("""
                        UPDATE device SET status = ?, version = ?
                         WHERE id = ? AND version = ? AND status = ?
                        """,
                device.status().name(), device.version(), device.id(), previousVersion,
                previousStatus.name());
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "device " + device.id() + " was changed by another transaction");
        }
        insertHistory(device.id(), Math.toIntExact(device.version()), previousStatus,
                device.status(), actorId, occurredAt);
        return device;
    }

    @Override
    @Transactional
    public DeviceBinding createBinding(DeviceBinding binding) {
        Device device = lockDevice(binding.deviceId());
        if (!device.tenantId().equals(binding.tenantId())) {
            throw new DeviceConflictException("device does not belong to binding tenant");
        }
        long overlaps = overlapCount(binding);
        if (overlaps > 0) {
            throw new DeviceConflictException("device binding overlaps an existing effective period");
        }
        jdbcTemplate.update("""
                        INSERT INTO device_binding (
                            id, tenant_id, device_id, target_type, target_id,
                            valid_from, valid_to, created_by, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                binding.id(), binding.tenantId(), binding.deviceId(), binding.targetType().name(),
                binding.targetId(), timestamp(binding.validFrom()), nullableTimestamp(binding.validTo()),
                binding.createdBy(), timestamp(binding.createdAt()));
        return binding;
    }

    @Override
    @Transactional
    public DeviceBinding closeBinding(String deviceId, String bindingId, Instant validTo) {
        lockDevice(deviceId);
        List<DeviceBinding> bindings = jdbcTemplate.query("""
                        SELECT id, tenant_id, device_id, target_type, target_id,
                               valid_from, valid_to, created_by, created_at
                          FROM device_binding
                         WHERE id = ? AND device_id = ?
                        """, this::mapBinding, bindingId, deviceId);
        DeviceBinding binding = bindings.stream().findFirst()
                .orElseThrow(() -> new DeviceConflictException("device binding not found"));
        if (!validTo.isAfter(binding.validFrom())) {
            throw new IllegalArgumentException("binding close time must be after validFrom");
        }
        if (binding.validTo() != null) {
            throw new DeviceConflictException("device binding is already closed");
        }
        jdbcTemplate.update("UPDATE device_binding SET valid_to = ? WHERE id = ? AND valid_to IS NULL",
                timestamp(validTo), bindingId);
        return new DeviceBinding(
                binding.id(), binding.tenantId(), binding.deviceId(), binding.targetType(),
                binding.targetId(), binding.validFrom(), validTo,
                binding.createdBy(), binding.createdAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceEligibility eligibilityAt(
            String tenantId, String deviceKey, String elderId, Instant observedAt
    ) {
        List<DeviceStatus> statuses = jdbcTemplate.query("""
                        SELECT status FROM device WHERE tenant_id = ? AND device_key = ?
                        """, (resultSet, rowNumber) ->
                        DeviceStatus.valueOf(resultSet.getString("status")), tenantId, deviceKey);
        if (statuses.isEmpty()) {
            return DeviceEligibility.rejected("device is not registered in tenant");
        }
        if (statuses.getFirst() != DeviceStatus.ACTIVE) {
            return DeviceEligibility.rejected("device is not active");
        }
        Integer matches = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                          FROM device_binding b
                          JOIN device d ON d.id = b.device_id
                         WHERE d.tenant_id = ? AND d.device_key = ?
                           AND b.target_type = 'ELDER' AND b.target_id = ?
                           AND b.valid_from <= ?
                           AND (b.valid_to IS NULL OR b.valid_to > ?)
                        """, Integer.class, tenantId, deviceKey, elderId,
                timestamp(observedAt), timestamp(observedAt));
        if (matches == null || matches != 1) {
            return DeviceEligibility.rejected(
                    "device has no matching elder binding at event observation time");
        }
        return DeviceEligibility.accepted();
    }

    private Device lockDevice(String deviceId) {
        List<Device> devices = jdbcTemplate.query("""
                        SELECT id, tenant_id, device_key, product_id, status, version, registered_at
                          FROM device WHERE id = ? FOR UPDATE
                        """, this::mapDevice, deviceId);
        return devices.stream().findFirst().orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }

    private long overlapCount(DeviceBinding binding) {
        String endCondition = binding.validTo() == null ? "" : " AND valid_from < ?";
        // Parentheses are required because the nullable end condition is an OR predicate.
        String sql = "SELECT COUNT(*) FROM device_binding WHERE tenant_id = ? AND device_id = ? "
                + "AND (valid_to IS NULL OR valid_to > ?)" + endCondition;
        Long count = binding.validTo() == null
                ? jdbcTemplate.queryForObject(sql, Long.class,
                binding.tenantId(), binding.deviceId(), timestamp(binding.validFrom()))
                : jdbcTemplate.queryForObject(sql, Long.class,
                binding.tenantId(), binding.deviceId(), timestamp(binding.validFrom()),
                timestamp(binding.validTo()));
        return count == null ? 0 : count;
    }

    private Device mapDevice(ResultSet resultSet, int rowNumber) throws SQLException {
        return Device.restore(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("device_key"), resultSet.getString("product_id"),
                DeviceStatus.valueOf(resultSet.getString("status")), resultSet.getLong("version"),
                resultSet.getTimestamp("registered_at").toInstant());
    }

    private DeviceBinding mapBinding(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp validTo = resultSet.getTimestamp("valid_to");
        return new DeviceBinding(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("device_id"),
                BindingTargetType.valueOf(resultSet.getString("target_type")),
                resultSet.getString("target_id"), resultSet.getTimestamp("valid_from").toInstant(),
                validTo == null ? null : validTo.toInstant(), resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private void insertHistory(
            String deviceId,
            int sequenceNumber,
            DeviceStatus previous,
            DeviceStatus next,
            String actorId,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO device_status_history (
                            id, device_id, sequence_no, from_status, to_status, actor_id, occurred_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), deviceId, sequenceNumber,
                previous == null ? null : previous.name(), next.name(), actorId,
                timestamp(occurredAt));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : timestamp(instant);
    }
}
