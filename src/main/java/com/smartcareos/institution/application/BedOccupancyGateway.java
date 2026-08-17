package com.smartcareos.institution.application;

import com.smartcareos.institution.domain.BedStatus;

public interface BedOccupancyGateway {

    BedReference lockBed(String tenantId, String bedId);

    void changeOccupancy(BedReference bed, BedStatus expected, BedStatus next);

    record BedReference(
            String id,
            String tenantId,
            String institutionId,
            BedStatus status,
            long version
    ) {
    }
}

