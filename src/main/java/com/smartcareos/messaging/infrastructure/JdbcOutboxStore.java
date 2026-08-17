package com.smartcareos.messaging.infrastructure;

import com.smartcareos.messaging.application.OutboxStore;
import com.smartcareos.messaging.domain.OutboxMessage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public List<OutboxMessage> claimBatch(
            String workerId,
            int batchSize,
            Instant now,
            Duration lease
    ) {
        Instant leaseExpiredBefore = now.minus(lease);
        List<String> candidates = jdbcTemplate.queryForList("""
                        SELECT event_id
                          FROM outbox_event
                         WHERE published_at IS NULL
                           AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                           AND (locked_at IS NULL OR locked_at < ?)
                         ORDER BY occurred_at, event_id
                         LIMIT ?
                        """,
                String.class,
                timestamp(now),
                timestamp(leaseExpiredBefore),
                batchSize);

        List<OutboxMessage> claimed = new ArrayList<>();
        for (String eventId : candidates) {
            int updated = jdbcTemplate.update("""
                            UPDATE outbox_event
                               SET locked_by = ?, locked_at = ?, attempt_count = attempt_count + 1
                             WHERE event_id = ?
                               AND published_at IS NULL
                               AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                               AND (locked_at IS NULL OR locked_at < ?)
                            """,
                    workerId,
                    timestamp(now),
                    eventId,
                    timestamp(now),
                    timestamp(leaseExpiredBefore));
            if (updated == 1) {
                claimed.add(findClaimed(eventId, workerId));
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    @Transactional
    public void markPublished(String eventId, String workerId, Instant publishedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE outbox_event
                           SET published_at = ?, next_attempt_at = NULL, last_error = NULL,
                               locked_by = NULL, locked_at = NULL
                         WHERE event_id = ? AND published_at IS NULL AND locked_by = ?
                        """,
                timestamp(publishedAt), eventId, workerId);
        requireSingleUpdate(updated, eventId, workerId);
    }

    @Override
    @Transactional
    public void markFailed(String eventId, String workerId, String error, Instant nextAttemptAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE outbox_event
                           SET next_attempt_at = ?, last_error = ?, locked_by = NULL, locked_at = NULL
                         WHERE event_id = ? AND published_at IS NULL AND locked_by = ?
                        """,
                timestamp(nextAttemptAt), error, eventId, workerId);
        requireSingleUpdate(updated, eventId, workerId);
    }

    private OutboxMessage findClaimed(String eventId, String workerId) {
        return jdbcTemplate.queryForObject("""
                        SELECT event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                               schema_version, occurred_at, payload, attempt_count
                          FROM outbox_event
                         WHERE event_id = ? AND locked_by = ? AND published_at IS NULL
                        """,
                (resultSet, rowNumber) -> new OutboxMessage(
                        resultSet.getString("event_id"),
                        resultSet.getString("tenant_id"),
                        resultSet.getString("aggregate_type"),
                        resultSet.getString("aggregate_id"),
                        resultSet.getString("event_type"),
                        resultSet.getInt("schema_version"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("payload"),
                        resultSet.getInt("attempt_count")),
                eventId,
                workerId);
    }

    private static void requireSingleUpdate(int updated, String eventId, String workerId) {
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "outbox event " + eventId + " is no longer owned by worker " + workerId);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
