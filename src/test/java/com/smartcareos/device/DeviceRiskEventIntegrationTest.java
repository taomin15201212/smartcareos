package com.smartcareos.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.device.application.DeviceRiskEventHandler;
import com.smartcareos.device.application.DeviceRegistryService;
import com.smartcareos.device.application.DeviceSnapshot;
import com.smartcareos.device.domain.RiskSourceEvent;
import com.smartcareos.device.domain.RiskSourceEventRepository;
import com.smartcareos.elder.application.ElderService;
import com.smartcareos.messaging.application.InboxStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:smartcareos_device_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceRiskEventIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DeviceRiskEventHandler handler;

    @Autowired
    InboxStore inboxStore;

    @Autowired
    RiskSourceEventRepository eventRepository;

    @Autowired
    AlarmApplicationService alarmService;

    @Autowired
    DeviceRegistryService deviceRegistryService;

    @Autowired
    ElderService elderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String elderId;

    @BeforeAll
    void registerActiveBoundDevice() {
        elderId = elderService.create(
                "tenant-device-test", "elder-device-001", "Device test elder").id();
        var product = deviceRegistryService.createProduct(
                "tenant-device-test", "emergency-button", "Emergency button");
        DeviceSnapshot device = deviceRegistryService.register(
                "tenant-device-test", "button-001", product.id(), "test-setup");
        deviceRegistryService.activate(device.id(), "test-setup");
        deviceRegistryService.bindElder(
                device.id(), elderId, Instant.parse("2026-08-14T00:00:00Z"),
                null, "test-setup");
    }

    @Test
    void createsOneAlarmForDuplicateEventsAndRejectsChangedPayload() throws Exception {
        String request = eventRequest("risk-event-api-1", "HIGH");

        String firstResponse = mockMvc.perform(post("/api/v1/device-risk-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"))
                .andExpect(jsonPath("$.alarmStatus").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(firstResponse).get("alarmId").asText();

        mockMvc.perform(post("/api/v1/device-risk-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"))
                .andExpect(jsonPath("$.alarmId").value(alarmId));

        mockMvc.perform(post("/api/v1/device-risk-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest("risk-event-api-1", "CRITICAL")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_PAYLOAD_CONFLICT"));

        assertEventChainCounts("tenant-device-test", "risk-event-api-1", alarmId, 1);
    }

    @Test
    void concurrentDuplicatesStillCreateOnlyOneEventAndAlarm() throws Exception {
        RiskSourceEvent event = event("risk-event-concurrent-1", AlarmSeverity.CRITICAL);
        int callerCount = 6;
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(callerCount)) {
            List<Callable<DeviceRiskEventHandler.HandleResult>> calls = java.util.stream.IntStream
                    .range(0, callerCount)
                    .mapToObj(index -> (Callable<DeviceRiskEventHandler.HandleResult>) () -> {
                        ready.countDown();
                        start.await();
                        return handler.handle(event);
                    })
                    .toList();
            List<java.util.concurrent.Future<DeviceRiskEventHandler.HandleResult>> futures = calls.stream()
                    .map(executor::submit)
                    .toList();
            ready.await();
            start.countDown();

            List<DeviceRiskEventHandler.HandleResult> results = futures.stream()
                    .map(DeviceRiskEventIntegrationTest::join)
                    .toList();
            assertThat(results)
                    .filteredOn(result -> result.outcome() == DeviceRiskEventHandler.Outcome.PROCESSED)
                    .hasSize(1);
            assertThat(results)
                    .filteredOn(result -> result.outcome() == DeviceRiskEventHandler.Outcome.DUPLICATE)
                    .hasSize(callerCount - 1);
            assertThat(results).extracting(result -> result.alarm().id()).containsOnly(
                    results.getFirst().alarm().id());
            assertEventChainCounts(
                    event.tenantId(), event.eventId(), results.getFirst().alarm().id(), 1);
        }
    }

    @Test
    void rollsBackInboxRiskEventAlarmAndOutboxWhenProcessingFails() {
        RiskSourceEvent event = event("risk-event-rollback-1", AlarmSeverity.MEDIUM);
        Instant receivedAt = Instant.parse("2026-08-15T00:00:01Z");

        assertThatThrownBy(() -> inboxStore.processOnce(
                "rollback-proof-consumer",
                event.tenantId(),
                event.eventId(),
                "a".repeat(64),
                receivedAt,
                () -> {
                    eventRepository.save(event, receivedAt);
                    alarmService.create(new AlarmApplicationService.CreateCommand(
                            event.tenantId(), event.elderId(), event.eventId(), event.severity()));
                    throw new IllegalStateException("simulated processing failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated processing failure");

        assertThat(countInbox("rollback-proof-consumer", event)).isZero();
        assertThat(countRiskEvent(event)).isZero();
        assertThat(countAlarm(event)).isZero();
        assertThat(countOutboxForSourceEvent(event)).isZero();

        DeviceRiskEventHandler.HandleResult retry = handler.handle(event);
        assertThat(retry.outcome()).isEqualTo(DeviceRiskEventHandler.Outcome.PROCESSED);
        assertEventChainCounts(event.tenantId(), event.eventId(), retry.alarm().id(), 1);
    }

    private String eventRequest(String eventId, String severity) {
        return """
                {
                  "eventId": "%s",
                  "tenantId": "tenant-device-test",
                  "deviceId": "button-001",
                  "elderId": "%s",
                  "severity": "%s",
                  "observedAt": "2026-08-15T00:00:00Z"
                }
                """.formatted(eventId, elderId, severity);
    }

    private RiskSourceEvent event(String eventId, AlarmSeverity severity) {
        return new RiskSourceEvent(
                eventId,
                "tenant-device-test",
                "button-001",
                elderId,
                severity,
                Instant.parse("2026-08-15T00:00:00Z"));
    }

    private void assertEventChainCounts(
            String tenantId,
            String eventId,
            String alarmId,
            long expected
    ) {
        assertThat(queryCount(
                "SELECT COUNT(*) FROM inbox_message WHERE tenant_id = ? AND event_id = ?",
                tenantId, eventId)).isEqualTo(expected);
        assertThat(queryCount(
                "SELECT COUNT(*) FROM device_risk_event WHERE tenant_id = ? AND event_id = ?",
                tenantId, eventId)).isEqualTo(expected);
        assertThat(queryCount(
                "SELECT COUNT(*) FROM alarm WHERE tenant_id = ? AND source_event_id = ?",
                tenantId, eventId)).isEqualTo(expected);
        assertThat(queryCount(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?",
                alarmId)).isEqualTo(expected);
    }

    private long countInbox(String consumer, RiskSourceEvent event) {
        return queryCount("""
                SELECT COUNT(*) FROM inbox_message
                 WHERE consumer_name = ? AND tenant_id = ? AND event_id = ?
                """, consumer, event.tenantId(), event.eventId());
    }

    private long countRiskEvent(RiskSourceEvent event) {
        return queryCount(
                "SELECT COUNT(*) FROM device_risk_event WHERE tenant_id = ? AND event_id = ?",
                event.tenantId(), event.eventId());
    }

    private long countAlarm(RiskSourceEvent event) {
        return queryCount(
                "SELECT COUNT(*) FROM alarm WHERE tenant_id = ? AND source_event_id = ?",
                event.tenantId(), event.eventId());
    }

    private long countOutboxForSourceEvent(RiskSourceEvent event) {
        return queryCount("""
                SELECT COUNT(*)
                  FROM outbox_event o
                  JOIN alarm a ON a.id = o.aggregate_id
                 WHERE a.tenant_id = ? AND a.source_event_id = ?
                """, event.tenantId(), event.eventId());
    }

    private long queryCount(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private static DeviceRiskEventHandler.HandleResult join(
            java.util.concurrent.Future<DeviceRiskEventHandler.HandleResult> future
    ) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("concurrent risk event handling failed", exception);
        }
    }
}
