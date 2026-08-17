package com.smartcareos.elder.application;

import com.smartcareos.elder.domain.Admission;

import java.time.Instant;

public record AdmissionSnapshot(
        String id,
        String tenantId,
        String elderId,
        String institutionId,
        String bedId,
        Instant admittedAt,
        Instant dischargedAt,
        String admittedBy,
        String dischargedBy,
        long version
) {
    static AdmissionSnapshot from(Admission admission) {
        return new AdmissionSnapshot(
                admission.id(), admission.tenantId(), admission.elderId(),
                admission.institutionId(), admission.bedId(), admission.admittedAt(),
                admission.dischargedAt(), admission.admittedBy(), admission.dischargedBy(),
                admission.version());
    }
}

