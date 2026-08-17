package com.smartcareos.alarm;

import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.domain.AlarmSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AlarmPersistenceIntegrationTest {

    @Autowired
    AlarmApplicationService alarmService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void storesAggregateTransitionsAndOutboxEventsIdempotently() {
        AlarmApplicationService.CreateCommand command = new AlarmApplicationService.CreateCommand(
                "institution-persistence-test",
                "elder-sensitive-300",
                "device-event-persistence-300",
                AlarmSeverity.CRITICAL);

        AlarmApplicationService.CreateResult first = alarmService.create(command);
        AlarmApplicationService.CreateResult duplicate = alarmService.create(command);
        String alarmId = first.alarm().id();

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.alarm().id()).isEqualTo(alarmId);
        assertThat(count("alarm", alarmId)).isEqualTo(1);
        assertThat(count("alarm_transition", alarmId)).isEqualTo(1);
        assertThat(count("outbox_event", alarmId)).isEqualTo(1);

        alarmService.acknowledge(alarmId, "staff-persistence-1");

        assertThat(count("alarm_transition", alarmId)).isEqualTo(2);
        assertThat(count("outbox_event", alarmId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM alarm WHERE id = ?", Long.class, alarmId)).isEqualTo(1L);

        List<String> eventTypes = jdbcTemplate.queryForList("""
                SELECT event_type
                  FROM outbox_event
                 WHERE aggregate_id = ?
                 ORDER BY occurred_at, event_type
                """, String.class, alarmId);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "AlarmCreated.v1", "AlarmAcknowledged.v1");

        List<String> payloads = jdbcTemplate.queryForList(
                "SELECT payload FROM outbox_event WHERE aggregate_id = ?",
                String.class,
                alarmId);
        assertThat(payloads)
                .allSatisfy(payload -> {
                    assertThat(payload).contains(alarmId, "institution-persistence-test");
                    assertThat(payload).doesNotContain("elder-sensitive-300");
                });
    }

    private long count(String table, String alarmId) {
        String idColumn = table.equals("alarm") ? "id" : "alarm_id";
        if (table.equals("outbox_event")) {
            idColumn = "aggregate_id";
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ?",
                Long.class,
                alarmId);
    }
}
