package com.smartcareos.alarm.infrastructure;

import com.smartcareos.alarm.domain.Alarm;
import com.smartcareos.alarm.domain.AlarmId;
import com.smartcareos.alarm.domain.AlarmRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Repository
@Profile("memory")
public class InMemoryAlarmRepository implements AlarmRepository {

    private final Map<AlarmId, Alarm> alarms = new HashMap<>();
    private final Map<String, AlarmId> sourceEventIndex = new HashMap<>();

    @Override
    public synchronized SaveResult saveIfAbsent(Alarm alarm) {
        String idempotencyKey = idempotencyKey(alarm.tenantId(), alarm.sourceEventId());
        AlarmId existingId = sourceEventIndex.get(idempotencyKey);
        if (existingId != null) {
            return new SaveResult(alarms.get(existingId), false);
        }

        alarms.put(alarm.id(), alarm);
        sourceEventIndex.put(idempotencyKey, alarm.id());
        return new SaveResult(alarm, true);
    }

    @Override
    public synchronized Optional<Alarm> findById(AlarmId alarmId) {
        return Optional.ofNullable(alarms.get(alarmId));
    }

    @Override
    public synchronized Optional<Alarm> findBySourceEvent(String tenantId, String sourceEventId) {
        AlarmId alarmId = sourceEventIndex.get(idempotencyKey(tenantId, sourceEventId));
        return alarmId == null ? Optional.empty() : Optional.ofNullable(alarms.get(alarmId));
    }

    @Override
    public synchronized Alarm update(AlarmId alarmId, Consumer<Alarm> change) {
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            throw new IllegalStateException("alarm not found");
        }
        change.accept(alarm);
        return alarm;
    }

    private static String idempotencyKey(String tenantId, String sourceEventId) {
        return tenantId + "\u0000" + sourceEventId;
    }
}
