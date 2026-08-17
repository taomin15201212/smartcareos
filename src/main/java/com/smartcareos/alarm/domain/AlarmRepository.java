package com.smartcareos.alarm.domain;

import java.util.Optional;
import java.util.function.Consumer;

public interface AlarmRepository {

    SaveResult saveIfAbsent(Alarm alarm);

    Optional<Alarm> findById(AlarmId alarmId);

    Optional<Alarm> findBySourceEvent(String tenantId, String sourceEventId);

    Alarm update(AlarmId alarmId, Consumer<Alarm> change);

    record SaveResult(Alarm alarm, boolean created) {
    }
}
