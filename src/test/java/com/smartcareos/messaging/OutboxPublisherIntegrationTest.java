package com.smartcareos.messaging;

import com.smartcareos.messaging.application.OutboxEventTransport;
import com.smartcareos.messaging.application.OutboxPublisher;
import com.smartcareos.messaging.application.OutboxStore;
import com.smartcareos.messaging.domain.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:smartcareos_outbox_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
class OutboxPublisherIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Autowired
    OutboxStore store;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_event");
    }

    @Test
    void retriesAfterBackoffAndMarksTheEventPublished() {
        String eventId = insertEvent(NOW.minusSeconds(1));
        OutboxPublisher failingPublisher = publisher(
                "worker-failing",
                Clock.fixed(NOW, ZoneOffset.UTC),
                message -> {
                    throw new IllegalStateException("simulated broker outage");
                });

        OutboxPublisher.PublishBatchResult failed = failingPublisher.publishBatch();

        assertThat(failed).isEqualTo(new OutboxPublisher.PublishBatchResult(1, 0, 1));
        assertThat(integerValue(eventId, "attempt_count")).isEqualTo(1);
        assertThat(stringValue(eventId, "last_error")).contains("simulated broker outage");
        assertThat(timestampValue(eventId, "next_attempt_at")).isEqualTo(NOW.plusSeconds(2));
        assertThat(timestampValue(eventId, "published_at")).isNull();

        List<OutboxMessage> delivered = new ArrayList<>();
        OutboxPublisher tooEarly = publisher(
                "worker-early",
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC),
                delivered::add);
        assertThat(tooEarly.publishBatch().claimed()).isZero();

        OutboxPublisher recovered = publisher(
                "worker-recovered",
                Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC),
                delivered::add);
        assertThat(recovered.publishBatch())
                .isEqualTo(new OutboxPublisher.PublishBatchResult(1, 1, 0));
        assertThat(delivered).singleElement().satisfies(message -> {
            assertThat(message.eventId()).isEqualTo(eventId);
            assertThat(message.attemptCount()).isEqualTo(2);
        });
        assertThat(integerValue(eventId, "attempt_count")).isEqualTo(2);
        assertThat(timestampValue(eventId, "published_at")).isEqualTo(NOW.plusSeconds(3));
        assertThat(stringValue(eventId, "last_error")).isNull();
    }

    @Test
    void anotherWorkerCanOnlyRecoverAnEventAfterItsLeaseExpires() {
        String eventId = insertEvent(NOW.minusSeconds(1));

        List<OutboxMessage> firstClaim = store.claimBatch(
                "worker-a", 10, NOW, Duration.ofMinutes(1));
        List<OutboxMessage> duringLease = store.claimBatch(
                "worker-b", 10, NOW.plusSeconds(30), Duration.ofMinutes(1));
        List<OutboxMessage> afterLease = store.claimBatch(
                "worker-b", 10, NOW.plusSeconds(61), Duration.ofMinutes(1));

        assertThat(firstClaim).singleElement().satisfies(message ->
                assertThat(message.attemptCount()).isEqualTo(1));
        assertThat(duringLease).isEmpty();
        assertThat(afterLease).singleElement().satisfies(message ->
                assertThat(message.attemptCount()).isEqualTo(2));
        assertThatThrownBy(() -> store.markPublished(eventId, "worker-a", NOW.plusSeconds(62)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        store.markPublished(eventId, "worker-b", NOW.plusSeconds(62));
        assertThat(timestampValue(eventId, "published_at")).isEqualTo(NOW.plusSeconds(62));
    }

    private OutboxPublisher publisher(String workerId, Clock clock, OutboxEventTransport transport) {
        return new OutboxPublisher(
                store,
                transport,
                clock,
                workerId,
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2),
                Duration.ofMinutes(15));
    }

    private String insertEvent(Instant occurredAt) {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                        INSERT INTO outbox_event (
                            event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                            schema_version, occurred_at, payload, published_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                        """,
                eventId,
                "tenant-outbox-test",
                "Alarm",
                UUID.randomUUID().toString(),
                "AlarmCreated.v1",
                1,
                Timestamp.from(occurredAt),
                "{\"status\":\"NEW\"}");
        return eventId;
    }

    private Integer integerValue(String eventId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM outbox_event WHERE event_id = ?",
                Integer.class,
                eventId);
    }

    private String stringValue(String eventId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM outbox_event WHERE event_id = ?",
                String.class,
                eventId);
    }

    private Instant timestampValue(String eventId, String column) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM outbox_event WHERE event_id = ?",
                Timestamp.class,
                eventId);
        return value == null ? null : value.toInstant();
    }
}
