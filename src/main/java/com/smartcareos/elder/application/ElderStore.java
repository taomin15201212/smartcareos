package com.smartcareos.elder.application;

import com.smartcareos.elder.domain.ElderStatus;

import java.time.Instant;
import java.util.Optional;

public interface ElderStore {

    ElderSnapshot create(ElderSnapshot elder);

    Optional<ElderSnapshot> find(String elderId);

    ElderSnapshot lockActive(String tenantId, String elderId);

    record ElderSnapshot(
            String id,
            String tenantId,
            String elderNo,
            String name,
            ElderStatus status,
            long version,
            Instant createdAt
    ) {
    }
}

