package com.smartcareos.care.application;

import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.application.AlarmSnapshot;
import com.smartcareos.care.domain.CareTask;
import com.smartcareos.care.domain.CareTaskStatus;
import com.smartcareos.elder.application.ElderDirectory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CareService {

    private final CareStore store;
    private final ElderDirectory elderDirectory;
    private final AlarmApplicationService alarmService;
    private final Clock clock;

    public CareService(
            CareStore store,
            ElderDirectory elderDirectory,
            AlarmApplicationService alarmService,
            Clock clock
    ) {
        this.store = store;
        this.elderDirectory = elderDirectory;
        this.alarmService = alarmService;
        this.clock = clock;
    }

    public CareStore.CarePlanSnapshot createPlan(
            String tenantId, String elderId, String name, String scheduleRule
    ) {
        elderDirectory.requireActiveElder(tenantId, elderId);
        return store.createPlan(new CareStore.CarePlanSnapshot(
                UUID.randomUUID().toString(), tenantId, elderId, name, scheduleRule,
                "DRAFT", 0, clock.instant()));
    }

    public CareStore.CarePlanSnapshot activatePlan(String planId) {
        CareStore.CarePlanSnapshot plan = store.findPlan(planId)
                .orElseThrow(() -> new CareNotFoundException("care plan", planId));
        if (!"DRAFT".equals(plan.status())) {
            throw new com.smartcareos.care.domain.CareConflictException(
                    "only a draft care plan can be activated");
        }
        return store.activatePlan(planId, plan.version());
    }

    public CareTaskSnapshot createPlanTask(
            String planId, String title, String assigneeId, Instant dueAt, String actorId
    ) {
        CareStore.CarePlanSnapshot plan = store.findPlan(planId)
                .orElseThrow(() -> new CareNotFoundException("care plan", planId));
        if (!"ACTIVE".equals(plan.status())) {
            throw new com.smartcareos.care.domain.CareConflictException(
                    "care plan must be active before creating tasks");
        }
        return CareTaskSnapshot.from(store.saveTask(CareTask.create(
                UUID.randomUUID().toString(), plan.tenantId(), plan.elderId(), plan.id(), null,
                title, assigneeId, dueAt, clock.instant()), actorId).task());
    }

    public CareStore.SaveTaskResult createAlarmTask(
            String alarmId, String title, String assigneeId, Instant dueAt, String actorId
    ) {
        AlarmSnapshot alarm = alarmService.get(alarmId);
        elderDirectory.requireActiveElder(alarm.tenantId(), alarm.elderId());
        CareStore.SaveTaskResult result = store.saveTask(CareTask.create(
                UUID.randomUUID().toString(), alarm.tenantId(), alarm.elderId(), null, alarm.id(),
                title, assigneeId, dueAt, clock.instant()), actorId);
        return result;
    }

    public CareTaskSnapshot getTask(String taskId) {
        return CareTaskSnapshot.from(store.findTask(taskId)
                .orElseThrow(() -> new CareNotFoundException("care task", taskId)));
    }

    public CareTaskSnapshot start(String taskId, String actorId) {
        return change(taskId, actorId, "START", null,
                (task, now) -> task.start());
    }

    public CareTaskSnapshot complete(
            String taskId, String actorId, String resultSummary
    ) {
        if (resultSummary == null || resultSummary.isBlank()) {
            throw new IllegalArgumentException("resultSummary is required");
        }
        return change(taskId, actorId, "COMPLETE", resultSummary,
                CareTask::complete);
    }

    public CareTaskSnapshot cancel(String taskId, String actorId) {
        return change(taskId, actorId, "CANCEL", null,
                (task, now) -> task.cancel());
    }

    private CareTaskSnapshot change(
            String taskId,
            String actorId,
            String action,
            String resultSummary,
            TaskChange change
    ) {
        CareTask task = store.findTask(taskId)
                .orElseThrow(() -> new CareNotFoundException("care task", taskId));
        long expectedVersion = task.version();
        Instant now = clock.instant();
        CareTaskStatus previous = change.apply(task, now);
        return CareTaskSnapshot.from(store.updateTask(
                task, expectedVersion, previous, action, actorId, now, resultSummary));
    }

    @FunctionalInterface
    private interface TaskChange {
        CareTaskStatus apply(CareTask task, Instant now);
    }
}
