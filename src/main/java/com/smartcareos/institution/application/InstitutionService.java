package com.smartcareos.institution.application;

import com.smartcareos.institution.domain.BedStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class InstitutionService {

    private final InstitutionStore store;
    private final Clock clock;

    public InstitutionService(InstitutionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public InstitutionStore.InstitutionSnapshot createInstitution(
            String tenantId, String institutionCode, String name
    ) {
        return store.createInstitution(new InstitutionStore.InstitutionSnapshot(
                UUID.randomUUID().toString(), tenantId, institutionCode, name,
                "ACTIVE", clock.instant()));
    }

    public InstitutionStore.RoomSnapshot createRoom(
            String institutionId, String roomCode, String name
    ) {
        InstitutionStore.InstitutionSnapshot institution = store.findInstitution(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException("institution", institutionId));
        return store.createRoom(new InstitutionStore.RoomSnapshot(
                UUID.randomUUID().toString(), institution.tenantId(), institution.id(),
                roomCode, name, clock.instant()));
    }

    public InstitutionStore.BedSnapshot createBed(String roomId, String bedCode) {
        InstitutionStore.RoomSnapshot room = store.findRoom(roomId)
                .orElseThrow(() -> new InstitutionNotFoundException("room", roomId));
        return store.createBed(new InstitutionStore.BedSnapshot(
                UUID.randomUUID().toString(), room.tenantId(), room.institutionId(), room.id(),
                bedCode, BedStatus.AVAILABLE, 0, clock.instant()));
    }

    public InstitutionStore.BedSnapshot getBed(String bedId) {
        return store.findBed(bedId)
                .orElseThrow(() -> new InstitutionNotFoundException("bed", bedId));
    }
}

