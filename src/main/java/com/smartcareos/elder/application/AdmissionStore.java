package com.smartcareos.elder.application;

import com.smartcareos.elder.domain.Admission;

import java.time.Instant;
import java.util.Optional;

public interface AdmissionStore {

    Admission save(Admission admission);

    Optional<Admission> findAdmission(String admissionId);

    Admission lockAdmission(String admissionId);

    boolean overlapsOpenAdmissionForElder(
            String tenantId, String elderId, Instant admittedAt
    );

    boolean overlapsOpenAdmissionForBed(
            String tenantId, String bedId, Instant admittedAt
    );

    Admission updateDischarge(Admission admission, long expectedVersion);
}
