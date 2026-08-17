package com.smartcareos.elder.application;

import com.smartcareos.elder.domain.ElderStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class ElderService implements ElderDirectory {

    private final ElderStore store;
    private final Clock clock;

    public ElderService(ElderStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public ElderStore.ElderSnapshot create(String tenantId, String elderNo, String name) {
        return store.create(new ElderStore.ElderSnapshot(
                UUID.randomUUID().toString(), tenantId, elderNo, name,
                ElderStatus.ACTIVE, 0, clock.instant()));
    }

    public ElderStore.ElderSnapshot get(String elderId) {
        return store.find(elderId)
                .orElseThrow(() -> new ElderNotFoundException("elder", elderId));
    }

    @Override
    public void requireActiveElder(String tenantId, String elderId) {
        ElderStore.ElderSnapshot elder = store.find(elderId)
                .orElseThrow(() -> new ElderNotFoundException("elder", elderId));
        if (!elder.tenantId().equals(tenantId) || elder.status() != ElderStatus.ACTIVE) {
            throw new ElderConflictException("active elder does not belong to tenant");
        }
    }
}

