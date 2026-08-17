package com.smartcareos.alarm.application;

import com.smartcareos.alarm.domain.Alarm;
import com.smartcareos.alarm.domain.AlarmId;
import com.smartcareos.alarm.domain.AlarmRepository;
import com.smartcareos.alarm.domain.AlarmSeverity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;

@Service
public class AlarmApplicationService {

    private final AlarmRepository repository;
    private final Clock clock;

    public AlarmApplicationService(AlarmRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public CreateResult create(CreateCommand command) {
        Alarm alarm = Alarm.create(
                command.tenantId(),
                command.elderId(),
                command.sourceEventId(),
                command.severity(),
                clock.instant()
        );
        AlarmRepository.SaveResult result = repository.saveIfAbsent(alarm);
        return new CreateResult(AlarmSnapshot.from(result.alarm()), result.created());
    }

    public AlarmSnapshot get(String alarmId) {
        return AlarmSnapshot.from(repository.findById(AlarmId.parse(alarmId))
                .orElseThrow(() -> new AlarmNotFoundException(alarmId)));
    }

    public Optional<AlarmSnapshot> findBySourceEvent(String tenantId, String sourceEventId) {
        return repository.findBySourceEvent(tenantId, sourceEventId).map(AlarmSnapshot::from);
    }

    public AlarmSnapshot acknowledge(String alarmId, String actorId) {
        return change(alarmId, actorId,
                (alarm, context) -> alarm.acknowledge(context.actorId(), context.occurredAt()));
    }

    public AlarmSnapshot start(String alarmId, String actorId) {
        return change(alarmId, actorId,
                (alarm, context) -> alarm.start(context.actorId(), context.occurredAt()));
    }

    public AlarmSnapshot resolve(String alarmId, String actorId) {
        return change(alarmId, actorId,
                (alarm, context) -> alarm.resolve(context.actorId(), context.occurredAt()));
    }

    public AlarmSnapshot close(String alarmId, String actorId) {
        return change(alarmId, actorId,
                (alarm, context) -> alarm.close(context.actorId(), context.occurredAt()));
    }

    public AlarmSnapshot escalate(String alarmId, String actorId) {
        return change(alarmId, actorId,
                (alarm, context) -> alarm.escalate(context.actorId(), context.occurredAt()));
    }

    private AlarmSnapshot change(
            String alarmId,
            String actorId,
            BiConsumer<Alarm, ChangeContext> change
    ) {
        AlarmId id = AlarmId.parse(alarmId);
        Instant now = clock.instant();
        try {
            Alarm updated = repository.update(id, alarm -> change.accept(alarm, new ChangeContext(actorId, now)));
            return AlarmSnapshot.from(updated);
        } catch (IllegalStateException exception) {
            throw new AlarmNotFoundException(alarmId);
        }
    }

    private record ChangeContext(String actorId, Instant occurredAt) {
    }

    public record CreateCommand(
            String tenantId,
            String elderId,
            String sourceEventId,
            AlarmSeverity severity
    ) {
    }

    public record CreateResult(AlarmSnapshot alarm, boolean created) {
    }
}
