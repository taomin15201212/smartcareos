package com.smartcareos.elder.application;

import com.smartcareos.elder.domain.Admission;
import com.smartcareos.institution.application.BedOccupancyGateway;
import com.smartcareos.institution.application.InstitutionConflictException;
import com.smartcareos.institution.domain.BedStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdmissionService {

    private final ElderStore elderStore;
    private final AdmissionStore admissionStore;
    private final BedOccupancyGateway bedGateway;
    private final Clock clock;

    public AdmissionService(
            ElderStore elderStore,
            AdmissionStore admissionStore,
            BedOccupancyGateway bedGateway,
            Clock clock
    ) {
        this.elderStore = elderStore;
        this.admissionStore = admissionStore;
        this.bedGateway = bedGateway;
        this.clock = clock;
    }

    @Transactional
    public AdmissionSnapshot admit(
            String tenantId,
            String elderId,
            String bedId,
            Instant admittedAt,
            String actorId
    ) {
        if (admittedAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("admittedAt cannot be in the future");
        }
        elderStore.lockActive(tenantId, elderId);
        BedOccupancyGateway.BedReference bed = bedGateway.lockBed(tenantId, bedId);
        if (admissionStore.overlapsOpenAdmissionForElder(tenantId, elderId, admittedAt)) {
            throw new ElderConflictException("elder admission overlaps an existing period");
        }
        if (admissionStore.overlapsOpenAdmissionForBed(tenantId, bedId, admittedAt)) {
            throw new InstitutionConflictException("bed admission overlaps an existing period");
        }
        if (bed.status() != BedStatus.AVAILABLE) {
            throw new InstitutionConflictException("bed is not available");
        }

        Admission admission = Admission.admit(
                UUID.randomUUID().toString(), tenantId, elderId, bed.institutionId(), bedId,
                admittedAt, actorId, clock.instant());
        admissionStore.save(admission);
        bedGateway.changeOccupancy(bed, BedStatus.AVAILABLE, BedStatus.OCCUPIED);
        return AdmissionSnapshot.from(admission);
    }

    public AdmissionSnapshot get(String admissionId) {
        return AdmissionSnapshot.from(admissionStore.findAdmission(admissionId)
                .orElseThrow(() -> new ElderNotFoundException("admission", admissionId)));
    }

    @Transactional
    public AdmissionSnapshot discharge(
            String admissionId, Instant dischargedAt, String actorId
    ) {
        if (dischargedAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("dischargedAt cannot be in the future");
        }
        Admission reference = admissionStore.findAdmission(admissionId)
                .orElseThrow(() -> new ElderNotFoundException("admission", admissionId));
        elderStore.lockActive(reference.tenantId(), reference.elderId());
        BedOccupancyGateway.BedReference bed =
                bedGateway.lockBed(reference.tenantId(), reference.bedId());
        Admission admission = admissionStore.lockAdmission(admissionId);
        long expectedVersion = admission.version();
        admission.discharge(dischargedAt, actorId);
        admissionStore.updateDischarge(admission, expectedVersion);
        bedGateway.changeOccupancy(bed, BedStatus.OCCUPIED, BedStatus.AVAILABLE);
        return AdmissionSnapshot.from(admission);
    }
}
