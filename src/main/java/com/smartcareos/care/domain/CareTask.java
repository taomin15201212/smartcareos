package com.smartcareos.care.domain;

import java.time.Instant;

public final class CareTask {

    private final String id;
    private final String tenantId;
    private final String elderId;
    private final String planId;
    private final String alarmId;
    private final String title;
    private CareTaskStatus status;
    private final String assigneeId;
    private final Instant dueAt;
    private long version;
    private final Instant createdAt;
    private Instant completedAt;

    private CareTask(
            String id, String tenantId, String elderId, String planId, String alarmId,
            String title, CareTaskStatus status, String assigneeId, Instant dueAt,
            long version, Instant createdAt, Instant completedAt
    ) {
        this.id = required(id, "id");
        this.tenantId = required(tenantId, "tenantId");
        this.elderId = required(elderId, "elderId");
        this.planId = planId;
        this.alarmId = alarmId;
        this.title = required(title, "title");
        this.status = java.util.Objects.requireNonNull(status, "status is required");
        this.assigneeId = required(assigneeId, "assigneeId");
        this.dueAt = java.util.Objects.requireNonNull(dueAt, "dueAt is required");
        this.version = version;
        this.createdAt = java.util.Objects.requireNonNull(createdAt, "createdAt is required");
        this.completedAt = completedAt;
    }

    public static CareTask create(
            String id, String tenantId, String elderId, String planId, String alarmId,
            String title, String assigneeId, Instant dueAt, Instant createdAt
    ) {
        return new CareTask(id, tenantId, elderId, planId, alarmId, title,
                CareTaskStatus.PENDING, assigneeId, dueAt, 0, createdAt, null);
    }

    public static CareTask restore(
            String id, String tenantId, String elderId, String planId, String alarmId,
            String title, CareTaskStatus status, String assigneeId, Instant dueAt,
            long version, Instant createdAt, Instant completedAt
    ) {
        return new CareTask(id, tenantId, elderId, planId, alarmId, title, status,
                assigneeId, dueAt, version, createdAt, completedAt);
    }

    public CareTaskStatus start() {
        requireStatus(CareTaskStatus.PENDING, "start");
        CareTaskStatus previous = status;
        status = CareTaskStatus.IN_PROGRESS;
        version++;
        return previous;
    }

    public CareTaskStatus complete(Instant occurredAt) {
        requireStatus(CareTaskStatus.IN_PROGRESS, "complete");
        CareTaskStatus previous = status;
        status = CareTaskStatus.COMPLETED;
        completedAt = occurredAt;
        version++;
        return previous;
    }

    public CareTaskStatus cancel() {
        if (status != CareTaskStatus.PENDING && status != CareTaskStatus.IN_PROGRESS) {
            throw new CareConflictException("cannot cancel care task in status " + status);
        }
        CareTaskStatus previous = status;
        status = CareTaskStatus.CANCELLED;
        version++;
        return previous;
    }

    private void requireStatus(CareTaskStatus expected, String action) {
        if (status != expected) {
            throw new CareConflictException(
                    "cannot " + action + " care task in status " + status);
        }
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String elderId() { return elderId; }
    public String planId() { return planId; }
    public String alarmId() { return alarmId; }
    public String title() { return title; }
    public CareTaskStatus status() { return status; }
    public String assigneeId() { return assigneeId; }
    public Instant dueAt() { return dueAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

