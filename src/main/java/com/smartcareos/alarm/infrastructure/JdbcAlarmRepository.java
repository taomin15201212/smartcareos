package com.smartcareos.alarm.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.alarm.domain.Alarm;
import com.smartcareos.alarm.domain.AlarmId;
import com.smartcareos.alarm.domain.AlarmRepository;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.alarm.domain.AlarmStatus;
import com.smartcareos.alarm.domain.AlarmTransition;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Repository
@Profile("!memory")
public class JdbcAlarmRepository implements AlarmRepository {

    private static final String SELECT_ALARM_BY_ID = """
            SELECT id, tenant_id, elder_id, source_event_id, severity, status,
                   escalation_level, version, created_at
              FROM alarm
             WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAlarmRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SaveResult saveIfAbsent(Alarm alarm) {
        int inserted = jdbcTemplate.update("""
                        INSERT IGNORE INTO alarm (
                            id, tenant_id, elder_id, source_event_id, severity, status,
                            escalation_level, version, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                alarm.id().toString(), alarm.tenantId(), alarm.elderId(), alarm.sourceEventId(),
                alarm.severity().name(), alarm.status().name(), alarm.escalationLevel(),
                alarm.version(), timestamp(alarm.createdAt()));

        if (inserted == 0) {
            Alarm existing = findBySourceEvent(alarm.tenantId(), alarm.sourceEventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "idempotent alarm insert was ignored but the existing alarm is not visible"));
            return new SaveResult(existing, false);
        }

        AlarmTransition transition = alarm.transitions().getFirst();
        insertTransition(alarm.id(), 0, transition);
        insertOutboxEvent(alarm, transition);
        return new SaveResult(alarm, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alarm> findById(AlarmId alarmId) {
        return queryAlarm(SELECT_ALARM_BY_ID, alarmId.toString());
    }

    @Override
    @Transactional
    public Alarm update(AlarmId alarmId, Consumer<Alarm> change) {
        Alarm alarm = findById(alarmId)
                .orElseThrow(() -> new IllegalStateException("alarm not found"));
        long expectedVersion = alarm.version();
        int previousTransitionCount = alarm.transitions().size();

        change.accept(alarm);

        int updated = jdbcTemplate.update("""
                        UPDATE alarm
                           SET status = ?, escalation_level = ?, version = ?
                         WHERE id = ? AND version = ?
                        """,
                alarm.status().name(), alarm.escalationLevel(), alarm.version(),
                alarm.id().toString(), expectedVersion);
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "alarm " + alarm.id() + " was changed by another transaction");
        }

        List<AlarmTransition> appended = alarm.transitions()
                .subList(previousTransitionCount, alarm.transitions().size());
        for (int index = 0; index < appended.size(); index++) {
            AlarmTransition transition = appended.get(index);
            insertTransition(alarm.id(), previousTransitionCount + index, transition);
            insertOutboxEvent(alarm, transition);
        }
        return alarm;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alarm> findBySourceEvent(String tenantId, String sourceEventId) {
        return queryAlarm("""
                SELECT id, tenant_id, elder_id, source_event_id, severity, status,
                       escalation_level, version, created_at
                  FROM alarm
                 WHERE tenant_id = ? AND source_event_id = ?
                """, tenantId, sourceEventId);
    }

    private Optional<Alarm> queryAlarm(String sql, Object... arguments) {
        List<Alarm> alarms = jdbcTemplate.query(sql, this::mapAlarm, arguments);
        return alarms.stream().findFirst();
    }

    private Alarm mapAlarm(ResultSet resultSet, int rowNumber) throws SQLException {
        AlarmId alarmId = AlarmId.parse(resultSet.getString("id"));
        List<AlarmTransition> transitions = jdbcTemplate.query("""
                        SELECT from_status, to_status, action, actor_id, occurred_at
                          FROM alarm_transition
                         WHERE alarm_id = ?
                         ORDER BY sequence_no
                        """,
                (transitionSet, transitionRow) -> new AlarmTransition(
                        nullableStatus(transitionSet.getString("from_status")),
                        AlarmStatus.valueOf(transitionSet.getString("to_status")),
                        transitionSet.getString("action"),
                        transitionSet.getString("actor_id"),
                        transitionSet.getTimestamp("occurred_at").toInstant()),
                alarmId.toString());

        return Alarm.restore(
                alarmId,
                resultSet.getString("tenant_id"),
                resultSet.getString("elder_id"),
                resultSet.getString("source_event_id"),
                AlarmSeverity.valueOf(resultSet.getString("severity")),
                AlarmStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("escalation_level"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                transitions);
    }

    private void insertTransition(AlarmId alarmId, int sequenceNumber, AlarmTransition transition) {
        jdbcTemplate.update("""
                        INSERT INTO alarm_transition (
                            id, alarm_id, sequence_no, from_status, to_status,
                            action, actor_id, occurred_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), alarmId.toString(), sequenceNumber,
                transition.from() == null ? null : transition.from().name(),
                transition.to().name(), transition.action(), transition.actorId(),
                timestamp(transition.occurredAt()));
    }

    private void insertOutboxEvent(Alarm alarm, AlarmTransition transition) {
        Map<String, Object> payload = Map.of(
                "alarmId", alarm.id().toString(),
                "tenantId", alarm.tenantId(),
                "status", alarm.status().name(),
                "severity", alarm.severity().name(),
                "version", alarm.version(),
                "action", transition.action(),
                "actorId", transition.actorId());

        jdbcTemplate.update("""
                        INSERT INTO outbox_event (
                            event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                            schema_version, occurred_at, payload, published_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                        """,
                UUID.randomUUID().toString(), alarm.tenantId(), "Alarm", alarm.id().toString(),
                eventType(transition.action()), 1, timestamp(transition.occurredAt()),
                writeJson(payload));
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize alarm outbox event", exception);
        }
    }

    private static String eventType(String action) {
        if (action.startsWith("ESCALATE")) {
            return "AlarmEscalated.v1";
        }
        return switch (action) {
            case "CREATE" -> "AlarmCreated.v1";
            case "ACKNOWLEDGE" -> "AlarmAcknowledged.v1";
            case "START" -> "AlarmHandlingStarted.v1";
            case "RESOLVE" -> "AlarmResolved.v1";
            case "CLOSE" -> "AlarmClosed.v1";
            default -> throw new IllegalArgumentException("unsupported alarm action: " + action);
        };
    }

    private static AlarmStatus nullableStatus(String value) {
        return value == null ? null : AlarmStatus.valueOf(value);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
