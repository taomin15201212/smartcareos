package com.smartcareos.alarm.application;

import com.smartcareos.alarm.domain.Alarm;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.alarm.domain.AlarmStatus;
import com.smartcareos.alarm.domain.AlarmTransition;

import java.time.Instant;
import java.util.List;

public record AlarmSnapshot(
        String id,
        String tenantId,
        String elderId,
        String sourceEventId,
        AlarmSeverity severity,
        AlarmStatus status,
        int escalationLevel,
        long version,
        Instant createdAt,
        List<AlarmTransition> transitions
) {
    public static AlarmSnapshot from(Alarm alarm) {
        return new AlarmSnapshot(
                alarm.id().toString(),
                alarm.tenantId(),
                alarm.elderId(),
                alarm.sourceEventId(),
                alarm.severity(),
                alarm.status(),
                alarm.escalationLevel(),
                alarm.version(),
                alarm.createdAt(),
                List.copyOf(alarm.transitions())
        );
    }
}
