package com.smartcareos.elder.domain;

import java.time.Instant;
import java.util.Objects;

public final class Admission {

    private final String id;
    private final String tenantId;
    private final String elderId;
    private final String institutionId;
    private final String bedId;
    private final Instant admittedAt;
    private Instant dischargedAt;
    private final String admittedBy;
    private String dischargedBy;
    private final Instant createdAt;
    private long version;

    private Admission(
            String id,
            String tenantId,
            String elderId,
            String institutionId,
            String bedId,
            Instant admittedAt,
            Instant dischargedAt,
            String admittedBy,
            String dischargedBy,
            Instant createdAt,
            long version
    ) {
        this.id = required(id, "id");
        this.tenantId = required(tenantId, "tenantId");
        this.elderId = required(elderId, "elderId");
        this.institutionId = required(institutionId, "institutionId");
        this.bedId = required(bedId, "bedId");
        this.admittedAt = Objects.requireNonNull(admittedAt, "admittedAt is required");
        this.dischargedAt = dischargedAt;
        this.admittedBy = required(admittedBy, "admittedBy");
        this.dischargedBy = dischargedBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.version = version;
    }

    public static Admission admit(
            String id,
            String tenantId,
            String elderId,
            String institutionId,
            String bedId,
            Instant admittedAt,
            String admittedBy,
            Instant createdAt
    ) {
        return new Admission(id, tenantId, elderId, institutionId, bedId,
                admittedAt, null, admittedBy, null, createdAt, 0);
    }

    public static Admission restore(
            String id,
            String tenantId,
            String elderId,
            String institutionId,
            String bedId,
            Instant admittedAt,
            Instant dischargedAt,
            String admittedBy,
            String dischargedBy,
            Instant createdAt,
            long version
    ) {
        return new Admission(id, tenantId, elderId, institutionId, bedId, admittedAt,
                dischargedAt, admittedBy, dischargedBy, createdAt, version);
    }

    public void discharge(Instant occurredAt, String actorId) {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (dischargedAt != null) {
            throw new AdmissionConflictException("admission is already discharged");
        }
        if (!occurredAt.isAfter(admittedAt)) {
            throw new IllegalArgumentException("dischargedAt must be after admittedAt");
        }
        dischargedAt = occurredAt;
        dischargedBy = required(actorId, "actorId");
        version++;
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String elderId() { return elderId; }
    public String institutionId() { return institutionId; }
    public String bedId() { return bedId; }
    public Instant admittedAt() { return admittedAt; }
    public Instant dischargedAt() { return dischargedAt; }
    public String admittedBy() { return admittedBy; }
    public String dischargedBy() { return dischargedBy; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

