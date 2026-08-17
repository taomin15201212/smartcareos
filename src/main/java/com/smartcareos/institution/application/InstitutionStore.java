package com.smartcareos.institution.application;

import com.smartcareos.institution.domain.BedStatus;

import java.time.Instant;
import java.util.Optional;

public interface InstitutionStore {

    InstitutionSnapshot createInstitution(InstitutionSnapshot institution);

    RoomSnapshot createRoom(RoomSnapshot room);

    BedSnapshot createBed(BedSnapshot bed);

    Optional<InstitutionSnapshot> findInstitution(String institutionId);

    Optional<RoomSnapshot> findRoom(String roomId);

    Optional<BedSnapshot> findBed(String bedId);

    record InstitutionSnapshot(
            String id,
            String tenantId,
            String institutionCode,
            String name,
            String status,
            Instant createdAt
    ) {
    }

    record RoomSnapshot(
            String id,
            String tenantId,
            String institutionId,
            String roomCode,
            String name,
            Instant createdAt
    ) {
    }

    record BedSnapshot(
            String id,
            String tenantId,
            String institutionId,
            String roomId,
            String bedCode,
            BedStatus status,
            long version,
            Instant createdAt
    ) {
    }
}

