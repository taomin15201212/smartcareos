package com.smartcareos.alarm.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Alarm {

    private final AlarmId id;
    private final String tenantId;
    private final String elderId;
    private final String sourceEventId;
    private final AlarmSeverity severity;
    private final Instant createdAt;
    private final List<AlarmTransition> transitions = new ArrayList<>();
    private AlarmStatus status;
    private int escalationLevel;
    private long version;

    private Alarm(
            AlarmId id,
            String tenantId,
            String elderId,
            String sourceEventId,
            AlarmSeverity severity,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = requireText(tenantId, "tenantId");
        this.elderId = requireText(elderId, "elderId");
        this.sourceEventId = requireText(sourceEventId, "sourceEventId");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = AlarmStatus.NEW;
        this.transitions.add(new AlarmTransition(null, AlarmStatus.NEW, "CREATE", "SYSTEM", createdAt));
    }

    public static Alarm create(
            String tenantId,
            String elderId,
            String sourceEventId,
            AlarmSeverity severity,
            Instant createdAt
    ) {
        return new Alarm(AlarmId.newId(), tenantId, elderId, sourceEventId, severity, createdAt);
    }

    public static Alarm restore(
            AlarmId id,
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
        Alarm alarm = new Alarm(id, tenantId, elderId, sourceEventId, severity, createdAt);
        alarm.status = Objects.requireNonNull(status, "status must not be null");
        if (escalationLevel < 0 || version < 0) {
            throw new IllegalArgumentException("escalationLevel and version must not be negative");
        }
        alarm.escalationLevel = escalationLevel;
        alarm.version = version;
        alarm.transitions.clear();
        alarm.transitions.addAll(List.copyOf(transitions));
        if (alarm.transitions.isEmpty()) {
            throw new IllegalArgumentException("restored alarm must contain transition history");
        }
        return alarm;
    }

    public void acknowledge(String actorId, Instant occurredAt) {
        transition(AlarmStatus.NEW, AlarmStatus.ACKNOWLEDGED, "ACKNOWLEDGE", actorId, occurredAt);
    }

    public void start(String actorId, Instant occurredAt) {
        transition(AlarmStatus.ACKNOWLEDGED, AlarmStatus.IN_PROGRESS, "START", actorId, occurredAt);
    }

    public void resolve(String actorId, Instant occurredAt) {
        transition(AlarmStatus.IN_PROGRESS, AlarmStatus.RESOLVED, "RESOLVE", actorId, occurredAt);
    }

    public void close(String actorId, Instant occurredAt) {
        transition(AlarmStatus.RESOLVED, AlarmStatus.CLOSED, "CLOSE", actorId, occurredAt);
    }

    public void escalate(String actorId, Instant occurredAt) {
        if (status == AlarmStatus.CLOSED) {
            throw new AlarmDomainException("closed alarm cannot be escalated");
        }
        requireActorAndTime(actorId, occurredAt);
        escalationLevel++;
        version++;
        transitions.add(new AlarmTransition(status, status, "ESCALATE_LEVEL_" + escalationLevel, actorId, occurredAt));
    }

    private void transition(
            AlarmStatus expected,
            AlarmStatus target,
            String action,
            String actorId,
            Instant occurredAt
    ) {
        if (status != expected) {
            throw new AlarmDomainException(
                    "cannot " + action.toLowerCase() + " alarm in status " + status
            );
        }
        requireActorAndTime(actorId, occurredAt);
        AlarmStatus previous = status;
        status = target;
        version++;
        transitions.add(new AlarmTransition(previous, target, action, actorId, occurredAt));
    }

    private void requireActorAndTime(String actorId, Instant occurredAt) {
        requireText(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (occurredAt.isBefore(createdAt)) {
            throw new AlarmDomainException("transition time cannot precede alarm creation");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public AlarmId id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String elderId() {
        return elderId;
    }

    public String sourceEventId() {
        return sourceEventId;
    }

    public AlarmSeverity severity() {
        return severity;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AlarmStatus status() {
        return status;
    }

    public int escalationLevel() {
        return escalationLevel;
    }

    public long version() {
        return version;
    }

    public List<AlarmTransition> transitions() {
        return Collections.unmodifiableList(transitions);
    }
}
