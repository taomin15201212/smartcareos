package com.smartcareos.messaging.infrastructure;

import com.smartcareos.messaging.application.InboxPayloadConflictException;
import com.smartcareos.messaging.application.InboxStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcInboxStore implements InboxStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean processOnce(
            String consumerName,
            String tenantId,
            String eventId,
            String payloadHash,
            Instant receivedAt,
            Runnable processing
    ) {
        int inserted = jdbcTemplate.update("""
                        INSERT IGNORE INTO inbox_message (
                            consumer_name, tenant_id, event_id, payload_hash, received_at, processed_at
                        ) VALUES (?, ?, ?, ?, ?, NULL)
                        """,
                consumerName, tenantId, eventId, payloadHash, Timestamp.from(receivedAt));

        if (inserted == 0) {
            String existingHash = jdbcTemplate.queryForObject("""
                            SELECT payload_hash
                              FROM inbox_message
                             WHERE consumer_name = ? AND tenant_id = ? AND event_id = ?
                            """,
                    String.class,
                    consumerName,
                    tenantId,
                    eventId);
            if (!payloadHash.equals(existingHash)) {
                throw new InboxPayloadConflictException(consumerName, tenantId, eventId);
            }
            return false;
        }

        processing.run();
        int completed = jdbcTemplate.update("""
                        UPDATE inbox_message
                           SET processed_at = ?
                         WHERE consumer_name = ? AND tenant_id = ? AND event_id = ?
                        """,
                Timestamp.from(receivedAt), consumerName, tenantId, eventId);
        if (completed != 1) {
            throw new IllegalStateException("new inbox message could not be marked processed: " + eventId);
        }
        return true;
    }
}
