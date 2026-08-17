package com.smartcareos.elder.infrastructure;

import com.smartcareos.elder.application.AdmissionStore;
import com.smartcareos.elder.application.ElderConflictException;
import com.smartcareos.elder.application.ElderNotFoundException;
import com.smartcareos.elder.application.ElderStore;
import com.smartcareos.elder.domain.Admission;
import com.smartcareos.elder.domain.ElderStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcElderStore implements ElderStore, AdmissionStore {

    private static final String SELECT_ADMISSION = """
            SELECT id, tenant_id, elder_id, institution_id, bed_id,
                   admitted_at, discharged_at, admitted_by, discharged_by,
                   created_at, version
              FROM admission
             WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcElderStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public ElderSnapshot create(ElderSnapshot elder) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO elder (
                                id, tenant_id, elder_no, name, status, version, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, elder.id(), elder.tenantId(), elder.elderNo(), elder.name(),
                    elder.status().name(), elder.version(), Timestamp.from(elder.createdAt()));
            return elder;
        } catch (DuplicateKeyException exception) {
            throw new ElderConflictException(
                    "elder number already exists in tenant: " + elder.elderNo(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElderSnapshot> find(String elderId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, elder_no, name, status, version, created_at
                          FROM elder WHERE id = ?
                        """, this::mapElder, elderId).stream().findFirst();
    }

    @Override
    @Transactional
    public ElderSnapshot lockActive(String tenantId, String elderId) {
        List<ElderSnapshot> elders = jdbcTemplate.query("""
                        SELECT id, tenant_id, elder_no, name, status, version, created_at
                          FROM elder
                         WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
                           FOR UPDATE
                        """, this::mapElder, elderId, tenantId);
        return elders.stream().findFirst()
                .orElseThrow(() -> new ElderNotFoundException("active elder", elderId));
    }

    @Override
    @Transactional
    public Admission save(Admission admission) {
        jdbcTemplate.update("""
                        INSERT INTO admission (
                            id, tenant_id, elder_id, institution_id, bed_id,
                            admitted_at, discharged_at, admitted_by, discharged_by,
                            created_at, version
                        ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?)
                        """, admission.id(), admission.tenantId(), admission.elderId(),
                admission.institutionId(), admission.bedId(),
                Timestamp.from(admission.admittedAt()), admission.admittedBy(),
                Timestamp.from(admission.createdAt()), admission.version());
        return admission;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Admission> findAdmission(String admissionId) {
        return jdbcTemplate.query(SELECT_ADMISSION, this::mapAdmission, admissionId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public Admission lockAdmission(String admissionId) {
        List<Admission> admissions = jdbcTemplate.query(
                SELECT_ADMISSION + " FOR UPDATE", this::mapAdmission, admissionId);
        return admissions.stream().findFirst()
                .orElseThrow(() -> new ElderNotFoundException("admission", admissionId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean overlapsOpenAdmissionForElder(
            String tenantId, String elderId, java.time.Instant admittedAt
    ) {
        return count("""
                SELECT COUNT(*) FROM admission
                 WHERE tenant_id = ? AND elder_id = ?
                   AND (discharged_at IS NULL OR discharged_at > ?)
                """, tenantId, elderId, Timestamp.from(admittedAt)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean overlapsOpenAdmissionForBed(
            String tenantId, String bedId, java.time.Instant admittedAt
    ) {
        return count("""
                SELECT COUNT(*) FROM admission
                 WHERE tenant_id = ? AND bed_id = ?
                   AND (discharged_at IS NULL OR discharged_at > ?)
                """, tenantId, bedId, Timestamp.from(admittedAt)) > 0;
    }

    @Override
    @Transactional
    public Admission updateDischarge(Admission admission, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                        UPDATE admission
                           SET discharged_at = ?, discharged_by = ?, version = ?
                         WHERE id = ? AND version = ? AND discharged_at IS NULL
                        """, Timestamp.from(admission.dischargedAt()), admission.dischargedBy(),
                admission.version(), admission.id(), expectedVersion);
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "admission " + admission.id() + " was changed by another transaction");
        }
        return admission;
    }

    private ElderSnapshot mapElder(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ElderSnapshot(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("elder_no"), resultSet.getString("name"),
                ElderStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private Admission mapAdmission(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp dischargedAt = resultSet.getTimestamp("discharged_at");
        return Admission.restore(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("elder_id"), resultSet.getString("institution_id"),
                resultSet.getString("bed_id"),
                resultSet.getTimestamp("admitted_at").toInstant(),
                dischargedAt == null ? null : dischargedAt.toInstant(),
                resultSet.getString("admitted_by"), resultSet.getString("discharged_by"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getLong("version"));
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }
}
