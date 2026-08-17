package com.smartcareos.alarm;

import com.smartcareos.alarm.domain.Alarm;
import com.smartcareos.alarm.domain.AlarmDomainException;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.alarm.domain.AlarmStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlarmDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void followsTheHappyPathWithoutTreatingEscalationAsAStatus() {
        Alarm alarm = Alarm.create("tenant-1", "elder-1", "event-1", AlarmSeverity.HIGH, CREATED_AT);

        alarm.escalate("system", CREATED_AT.plusSeconds(30));
        alarm.acknowledge("staff-1", CREATED_AT.plusSeconds(60));
        alarm.start("staff-1", CREATED_AT.plusSeconds(90));
        alarm.resolve("staff-1", CREATED_AT.plusSeconds(120));
        alarm.close("supervisor-1", CREATED_AT.plusSeconds(180));

        assertThat(alarm.status()).isEqualTo(AlarmStatus.CLOSED);
        assertThat(alarm.escalationLevel()).isEqualTo(1);
        assertThat(alarm.version()).isEqualTo(5);
        assertThat(alarm.transitions()).hasSize(6);
    }

    @Test
    void rejectsInvalidStateTransitions() {
        Alarm alarm = Alarm.create("tenant-1", "elder-1", "event-2", AlarmSeverity.MEDIUM, CREATED_AT);

        assertThatThrownBy(() -> alarm.close("staff-1", CREATED_AT.plusSeconds(10)))
                .isInstanceOf(AlarmDomainException.class)
                .hasMessageContaining("status NEW");
    }

    @Test
    void rejectsEscalationAfterClosure() {
        Alarm alarm = Alarm.create("tenant-1", "elder-1", "event-3", AlarmSeverity.CRITICAL, CREATED_AT);
        alarm.acknowledge("staff-1", CREATED_AT.plusSeconds(10));
        alarm.start("staff-1", CREATED_AT.plusSeconds(20));
        alarm.resolve("staff-1", CREATED_AT.plusSeconds(30));
        alarm.close("staff-1", CREATED_AT.plusSeconds(40));

        assertThatThrownBy(() -> alarm.escalate("system", CREATED_AT.plusSeconds(50)))
                .isInstanceOf(AlarmDomainException.class)
                .hasMessageContaining("closed alarm");
    }
}
