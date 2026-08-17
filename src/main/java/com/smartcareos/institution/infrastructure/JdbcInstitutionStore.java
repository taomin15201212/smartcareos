package com.smartcareos.institution.infrastructure;

import com.smartcareos.institution.application.BedOccupancyGateway;
import com.smartcareos.institution.application.InstitutionConflictException;
import com.smartcareos.institution.application.InstitutionNotFoundException;
import com.smartcareos.institution.application.InstitutionStore;
import com.smartcareos.institution.domain.BedStatus;
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
public class JdbcInstitutionStore implements InstitutionStore, BedOccupancyGateway {

    private static final String SELECT_BED = """
            SELECT b.id, b.tenant_id, r.institution_id, b.room_id, b.bed_code,
                   b.status, b.version, b.created_at
              FROM institution_bed b
              JOIN institution_room r ON r.id = b.room_id
             WHERE b.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcInstitutionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public InstitutionSnapshot createInstitution(InstitutionSnapshot institution) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO institution (
                                id, tenant_id, institution_code, name, status, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """, institution.id(), institution.tenantId(),
                    institution.institutionCode(), institution.name(), institution.status(),
                    Timestamp.from(institution.createdAt()));
            return institution;
        } catch (DuplicateKeyException exception) {
            throw new InstitutionConflictException(
                    "institution code already exists in tenant: "
                            + institution.institutionCode(), exception);
        }
    }

    @Override
    @Transactional
    public RoomSnapshot createRoom(RoomSnapshot room) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO institution_room (
                                id, tenant_id, institution_id, room_code, name, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """, room.id(), room.tenantId(), room.institutionId(), room.roomCode(),
                    room.name(), Timestamp.from(room.createdAt()));
            return room;
        } catch (DuplicateKeyException exception) {
            throw new InstitutionConflictException(
                    "room code already exists in institution: " + room.roomCode(), exception);
        }
    }

    @Override
    @Transactional
    public BedSnapshot createBed(BedSnapshot bed) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO institution_bed (
                                id, tenant_id, room_id, bed_code, status, version, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, bed.id(), bed.tenantId(), bed.roomId(), bed.bedCode(),
                    bed.status().name(), bed.version(), Timestamp.from(bed.createdAt()));
            return bed;
        } catch (DuplicateKeyException exception) {
            throw new InstitutionConflictException(
                    "bed code already exists in room: " + bed.bedCode(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstitutionSnapshot> findInstitution(String institutionId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, institution_code, name, status, created_at
                          FROM institution WHERE id = ?
                        """, (resultSet, rowNumber) -> new InstitutionSnapshot(
                        resultSet.getString("id"), resultSet.getString("tenant_id"),
                        resultSet.getString("institution_code"), resultSet.getString("name"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()), institutionId)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomSnapshot> findRoom(String roomId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, institution_id, room_code, name, created_at
                          FROM institution_room WHERE id = ?
                        """, (resultSet, rowNumber) -> new RoomSnapshot(
                        resultSet.getString("id"), resultSet.getString("tenant_id"),
                        resultSet.getString("institution_id"), resultSet.getString("room_code"),
                        resultSet.getString("name"),
                        resultSet.getTimestamp("created_at").toInstant()), roomId)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BedSnapshot> findBed(String bedId) {
        return jdbcTemplate.query(SELECT_BED, this::mapBed, bedId).stream().findFirst();
    }

    @Override
    @Transactional
    public BedReference lockBed(String tenantId, String bedId) {
        List<BedReference> beds = jdbcTemplate.query("""
                        SELECT b.id, b.tenant_id, r.institution_id, b.status, b.version
                          FROM institution_bed b
                          JOIN institution_room r ON r.id = b.room_id
                         WHERE b.id = ? AND b.tenant_id = ? FOR UPDATE
                        """, (resultSet, rowNumber) -> new BedReference(
                        resultSet.getString("id"), resultSet.getString("tenant_id"),
                        resultSet.getString("institution_id"),
                        BedStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("version")), bedId, tenantId);
        return beds.stream().findFirst()
                .orElseThrow(() -> new InstitutionNotFoundException("bed", bedId));
    }

    @Override
    @Transactional
    public void changeOccupancy(BedReference bed, BedStatus expected, BedStatus next) {
        if (bed.status() != expected) {
            throw new InstitutionConflictException(
                    "bed " + bed.id() + " must be " + expected + " but is " + bed.status());
        }
        int updated = jdbcTemplate.update("""
                        UPDATE institution_bed SET status = ?, version = version + 1
                         WHERE id = ? AND tenant_id = ? AND status = ? AND version = ?
                        """, next.name(), bed.id(), bed.tenantId(), expected.name(), bed.version());
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "bed " + bed.id() + " was changed by another transaction");
        }
    }

    private BedSnapshot mapBed(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BedSnapshot(
                resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("institution_id"), resultSet.getString("room_id"),
                resultSet.getString("bed_code"),
                BedStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}

