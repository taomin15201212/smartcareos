package com.smartcareos.care.application;

import com.smartcareos.care.domain.CareTask;
import com.smartcareos.care.domain.CareTaskStatus;

import java.time.Instant;
import java.util.Optional;

public interface CareStore {

    CarePlanSnapshot createPlan(CarePlanSnapshot plan);

    Optional<CarePlanSnapshot> findPlan(String planId);

    CarePlanSnapshot activatePlan(String planId, long expectedVersion);

    SaveTaskResult saveTask(CareTask task, String actorId);

    Optional<CareTask> findTask(String taskId);

    CareTask updateTask(
            CareTask task,
            long expectedVersion,
            CareTaskStatus previousStatus,
            String action,
            String actorId,
            Instant occurredAt,
            String resultSummary
    );

    record CarePlanSnapshot(
            String id,
            String tenantId,
            String elderId,
            String name,
            String scheduleRule,
            String status,
            long version,
            Instant createdAt
    ) {
    }

    record SaveTaskResult(CareTask task, boolean created) {
    }
}

