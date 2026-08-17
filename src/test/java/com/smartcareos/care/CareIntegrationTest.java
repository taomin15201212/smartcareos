package com.smartcareos.care;

import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.care.application.CareService;
import com.smartcareos.care.domain.CareConflictException;
import com.smartcareos.elder.application.ElderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:smartcareos_care_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
class CareIntegrationTest {
    @Autowired CareService care; @Autowired ElderService elders;
    @Autowired AlarmApplicationService alarms; @Autowired JdbcTemplate jdbc;

    @Test void planTaskRunsToCompletionWithRecordTransitionsAndEvents() {
        String tenant="care-"+UUID.randomUUID();
        String elder=elders.create(tenant,"e-"+UUID.randomUUID(),"Elder").id();
        var plan=care.createPlan(tenant,elder,"Daily care","0 0 8 * * *");
        assertThatThrownBy(()->care.createPlanTask(plan.id(),"Morning care","nurse",Instant.now().plusSeconds(60),"lead"))
                .isInstanceOf(CareConflictException.class);
        care.activatePlan(plan.id());
        var task=care.createPlanTask(plan.id(),"Morning care","nurse",Instant.now().plusSeconds(60),"lead");
        assertThat(care.start(task.id(),"nurse").status().name()).isEqualTo("IN_PROGRESS");
        assertThat(care.complete(task.id(),"nurse","completed normally").status().name()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_record WHERE task_id=?",Long.class,task.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_task_transition WHERE task_id=?",Long.class,task.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=?",Long.class,task.id())).isEqualTo(3);
        assertThatThrownBy(()->care.cancel(task.id(),"nurse")).isInstanceOf(CareConflictException.class);
    }

    @Test void alarmCreatesAtMostOneCareTask() {
        String tenant="alarm-care-"+UUID.randomUUID();
        String elder=elders.create(tenant,"e-"+UUID.randomUUID(),"Elder").id();
        String alarm=alarms.create(new AlarmApplicationService.CreateCommand(tenant,elder,"source-"+UUID.randomUUID(),AlarmSeverity.HIGH)).alarm().id();
        var first=care.createAlarmTask(alarm,"Respond","nurse",Instant.now().plusSeconds(60),"desk");
        var second=care.createAlarmTask(alarm,"Respond","nurse",Instant.now().plusSeconds(60),"desk");
        assertThat(first.created()).isTrue(); assertThat(second.created()).isFalse();
        assertThat(second.task().id()).isEqualTo(first.task().id());
    }
}
