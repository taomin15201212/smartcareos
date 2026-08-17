package com.smartcareos.care.application;

import com.smartcareos.care.domain.CareTask;
import com.smartcareos.care.domain.CareTaskStatus;

import java.time.Instant;

public record CareTaskSnapshot(
        String id,
        String tenantId,
        String elderId,
        String planId,
        String alarmId,
        String title,
        CareTaskStatus status,
        String assigneeId,
        Instant dueAt,
        long version,
        Instant createdAt,
        Instant completedAt
) {
    public static CareTaskSnapshot from(CareTask task) {
        return new CareTaskSnapshot(
                task.id(), task.tenantId(), task.elderId(), task.planId(), task.alarmId(),
                task.title(), task.status(), task.assigneeId(), task.dueAt(), task.version(),
                task.createdAt(), task.completedAt());
    }
}
