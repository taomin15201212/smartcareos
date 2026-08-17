package com.smartcareos.care.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.care.application.CareNotFoundException;
import com.smartcareos.care.application.CareStore;
import com.smartcareos.care.domain.CareConflictException;
import com.smartcareos.care.domain.CareTask;
import com.smartcareos.care.domain.CareTaskStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCareStore implements CareStore {

    private static final String SELECT_TASK = """
            SELECT id, tenant_id, elder_id, plan_id, alarm_id, title, status,
                   assignee_id, due_at, version, created_at, completed_at
              FROM care_task
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCareStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CarePlanSnapshot createPlan(CarePlanSnapshot plan) {
        jdbcTemplate.update("""
                        INSERT INTO care_plan (
                            id, tenant_id, elder_id, name, schedule_rule,
                            status, version, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, plan.id(), plan.tenantId(), plan.elderId(), plan.name(),
                plan.scheduleRule(), plan.status(), plan.version(), Timestamp.from(plan.createdAt()));
        return plan;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarePlanSnapshot> findPlan(String planId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, elder_id, name, schedule_rule,
                               status, version, created_at
                          FROM care_plan WHERE id = ?
                        """, (resultSet, rowNumber) -> new CarePlanSnapshot(
                        resultSet.getString("id"), resultSet.getString("tenant_id"),
                        resultSet.getString("elder_id"), resultSet.getString("name"),
                        resultSet.getString("schedule_rule"), resultSet.getString("status"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("created_at").toInstant()), planId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public CarePlanSnapshot activatePlan(String planId, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                        UPDATE care_plan SET status = 'ACTIVE', version = version + 1
                         WHERE id = ? AND status = 'DRAFT' AND version = ?
                        """, planId, expectedVersion);
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "care plan " + planId + " was changed by another transaction");
        }
        return findPlan(planId).orElseThrow(() -> new CareNotFoundException("care plan", planId));
    }

    @Override
    @Transactional
    public SaveTaskResult saveTask(CareTask task, String actorId) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO care_task (
                                id, tenant_id, elder_id, plan_id, alarm_id, title, status,
                                assignee_id, due_at, version, created_at, completed_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                            """, task.id(), task.tenantId(), task.elderId(), task.planId(),
                    task.alarmId(), task.title(), task.status().name(), task.assigneeId(),
                    Timestamp.from(task.dueAt()), task.version(), Timestamp.from(task.createdAt()));
        } catch (DuplicateKeyException exception) {
            if (task.alarmId() == null) {
                throw exception;
            }
            CareTask existing = findByAlarm(task.tenantId(), task.alarmId())
                    .orElseThrow(() -> new CareConflictException(
                            "alarm care task insert conflicted but existing task is not visible"));
            return new SaveTaskResult(existing, false);
        }
        insertTransition(task, 0, null, task.status(), "CREATE", actorId, task.createdAt());
        insertOutbox(task, "CareTaskCreated.v1", "CREATE", actorId, task.createdAt());
        return new SaveTaskResult(task, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CareTask> findTask(String taskId) {
        return jdbcTemplate.query(SELECT_TASK + " WHERE id = ?", this::mapTask, taskId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public CareTask updateTask(
            CareTask task,
            long expectedVersion,
            CareTaskStatus previousStatus,
            String action,
            String actorId,
            Instant occurredAt,
            String resultSummary
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE care_task
                           SET status = ?, version = ?, completed_at = ?
                         WHERE id = ? AND version = ? AND status = ?
                        """, task.status().name(), task.version(), timestamp(task.completedAt()),
                task.id(), expectedVersion, previousStatus.name());
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "care task " + task.id() + " was changed by another transaction");
        }
        insertTransition(task, Math.toIntExact(task.version()), previousStatus,
                task.status(), action, actorId, occurredAt);
        if (task.status() == CareTaskStatus.COMPLETED) {
            jdbcTemplate.update("""
                            INSERT INTO care_record (
                                id, tenant_id, task_id, elder_id, performed_by,
                                performed_at, result_summary, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, UUID.randomUUID().toString(), task.tenantId(), task.id(),
                    task.elderId(), actorId, Timestamp.from(occurredAt), resultSummary,
                    Timestamp.from(occurredAt));
        }
        insertOutbox(task, eventType(action), action, actorId, occurredAt);
        return task;
    }

    private Optional<CareTask> findByAlarm(String tenantId, String alarmId) {
        return jdbcTemplate.query(
                SELECT_TASK + " WHERE tenant_id = ? AND alarm_id = ?",
                this::mapTask, tenantId, alarmId).stream().findFirst();
    }

    private CareTask mapTask(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        return CareTask.restore(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("elder_id"), resultSet.getString("plan_id"),
                resultSet.getString("alarm_id"), resultSet.getString("title"),
                CareTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("assignee_id"), resultSet.getTimestamp("due_at").toInstant(),
                resultSet.getLong("version"), resultSet.getTimestamp("created_at").toInstant(),
                completedAt == null ? null : completedAt.toInstant());
    }

    private void insertTransition(
            CareTask task,
            int sequence,
            CareTaskStatus previous,
            CareTaskStatus next,
            String action,
            String actorId,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO care_task_transition (
                            id, task_id, sequence_no, from_status, to_status,
                            action, actor_id, occurred_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), task.id(), sequence,
                previous == null ? null : previous.name(), next.name(), action, actorId,
                Timestamp.from(occurredAt));
    }

    private void insertOutbox(
            CareTask task, String eventType, String action, String actorId, Instant occurredAt
    ) {
        Map<String, Object> payload = Map.of(
                "taskId", task.id(),
                "tenantId", task.tenantId(),
                "elderId", task.elderId(),
                "status", task.status().name(),
                "action", action,
                "actorId", actorId,
                "version", task.version());
        jdbcTemplate.update("""
                        INSERT INTO outbox_event (
                            event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                            schema_version, occurred_at, payload, published_at
                        ) VALUES (?, ?, 'CareTask', ?, ?, 1, ?, ?, NULL)
                        """, UUID.randomUUID().toString(), task.tenantId(), task.id(), eventType,
                Timestamp.from(occurredAt), writeJson(payload));
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize care task outbox event", exception);
        }
    }

    private static String eventType(String action) {
        return switch (action) {
            case "START" -> "CareTaskStarted.v1";
            case "COMPLETE" -> "CareTaskCompleted.v1";
            case "CANCEL" -> "CareTaskCancelled.v1";
            default -> throw new IllegalArgumentException("unsupported care task action: " + action);
        };
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

