package com.smartcareos.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.device.application.DeviceConflictException;
import com.smartcareos.device.application.DeviceEventRejectedException;
import com.smartcareos.device.application.DeviceRegistryService;
import com.smartcareos.device.application.DeviceRiskEventHandler;
import com.smartcareos.device.application.DeviceSnapshot;
import com.smartcareos.device.domain.RiskSourceEvent;
import com.smartcareos.elder.application.ElderService;
import com.smartcareos.elder.application.ElderConflictException;
import com.smartcareos.elder.application.ElderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
        "spring.datasource.url=jdbc:h2:mem:smartcareos_registry_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DeviceRegistryIntegrationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-15T00:00:00Z");

    @Autowired DeviceRegistryService registry;
    @Autowired ElderService elderService;
    @Autowired DeviceRiskEventHandler riskHandler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void exposesProductRegistrationActivationAndBindingApis() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String productResponse = mockMvc.perform(post("/api/v1/device-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"tenant-api","productKey":"button-%s","name":"Button"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productResponse).get("id").asText();

        String deviceResponse = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"tenant-api","deviceKey":"device-%s",\
                                "productId":"%s","actorId":"staff-api"}
                                """.formatted(suffix, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode deviceJson = objectMapper.readTree(deviceResponse);
        String deviceId = deviceJson.get("id").asText();
        String elderId = elder("tenant-api", "api");

        mockMvc.perform(post("/api/v1/devices/{id}/activate", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"staff-api\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/devices/{id}/bindings", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"elderId":"%s","validFrom":"2026-08-14T00:00:00Z",\
                                "createdBy":"staff-api"}
                                """.formatted(elderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("ELDER"))
                .andExpect(jsonPath("$.targetId").value(elderId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_status_history WHERE device_id = ?",
                Long.class, deviceId)).isEqualTo(2);
    }

    @Test
    void rejectsInactiveDisabledUnboundMismatchedAndOutOfPeriodDevices() {
        DeviceSnapshot inactive = registeredDevice("inactive");
        assertRejected(inactive, "elder-1", "inactive-event");

        DeviceSnapshot disabled = registeredDevice("disabled");
        String disabledElder = elder(disabled.tenantId(), "disabled");
        registry.activate(disabled.id(), "tester");
        registry.bindElder(disabled.id(), disabledElder, OBSERVED_AT.minusSeconds(60), null, "tester");
        registry.disable(disabled.id(), "tester");
        assertRejected(disabled, disabledElder, "disabled-event");

        DeviceSnapshot unbound = registeredDevice("unbound");
        registry.activate(unbound.id(), "tester");
        assertRejected(unbound, "elder-1", "unbound-event");

        DeviceSnapshot mismatch = registeredDevice("mismatch");
        String boundElder = elder(mismatch.tenantId(), "mismatch-bound");
        registry.activate(mismatch.id(), "tester");
        registry.bindElder(mismatch.id(), boundElder, OBSERVED_AT.minusSeconds(60), null, "tester");
        assertRejected(mismatch, "elder-1", "mismatch-event");

        DeviceSnapshot future = registeredDevice("future");
        String futureElder = elder(future.tenantId(), "future");
        registry.activate(future.id(), "tester");
        registry.bindElder(future.id(), futureElder, OBSERVED_AT.plusSeconds(60), null, "tester");
        assertRejected(future, futureElder, "future-event");
    }

    @Test
    void riskEventApiReturnsUnprocessableEntityAndRollsBackInboxWhenBindingIsInvalid()
            throws Exception {
        DeviceSnapshot device = registeredDevice("api-rejected");
        registry.activate(device.id(), "tester");

        mockMvc.perform(post("/api/v1/device-risk-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"api-rejected-event",
                                  "tenantId":"%s",
                                  "deviceId":"%s",
                                  "elderId":"elder-without-binding",
                                  "severity":"HIGH",
                                  "observedAt":"2026-08-15T00:00:00Z"
                                }
                                """.formatted(device.tenantId(), device.deviceKey())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEVICE_EVENT_REJECTED"));

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM inbox_message
                         WHERE tenant_id = ? AND event_id = 'api-rejected-event'
                        """, Long.class, device.tenantId())).isZero();
    }

    @Test
    void acceptsAValidHistoricalBindingAtEventObservationTime() {
        DeviceSnapshot device = registeredDevice("historical");
        String elderId = elder(device.tenantId(), "historical");
        registry.activate(device.id(), "tester");
        registry.bindElder(device.id(), elderId, OBSERVED_AT.minusSeconds(60),
                OBSERVED_AT.plusSeconds(60), "tester");

        DeviceRiskEventHandler.HandleResult result = riskHandler.handle(event(
                device, elderId, "historical-event"));

        assertThat(result.outcome()).isEqualTo(DeviceRiskEventHandler.Outcome.PROCESSED);
    }

    @Test
    void rejectsBindingToUnknownOrCrossTenantElder() {
        DeviceSnapshot device = registeredDevice("elder-validation");
        String otherTenantElder = elder("tenant-other", "other-tenant");

        assertThatThrownBy(() -> registry.bindElder(
                device.id(), "missing-elder", OBSERVED_AT, null, "tester"))
                .isInstanceOf(ElderNotFoundException.class);
        assertThatThrownBy(() -> registry.bindElder(
                device.id(), otherTenantElder, OBSERVED_AT, null, "tester"))
                .isInstanceOf(ElderConflictException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_binding WHERE device_id = ?",
                Long.class, device.id())).isZero();
    }

    @Test
    void rejectsSequentialOverlappingBindings() {
        DeviceSnapshot device = registeredDevice("overlap");
        String firstElder = elder(device.tenantId(), "overlap-first");
        String secondElder = elder(device.tenantId(), "overlap-second");
        registry.bindElder(device.id(), firstElder, OBSERVED_AT.minusSeconds(120),
                OBSERVED_AT.plusSeconds(120), "tester");

        assertThatThrownBy(() -> registry.bindElder(
                device.id(), secondElder, OBSERVED_AT, null, "tester"))
                .isInstanceOf(DeviceConflictException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void serializesConcurrentOverlappingBindingCreation() throws Exception {
        DeviceSnapshot device = registeredDevice("concurrent-binding");
        String firstElder = elder(device.tenantId(), "concurrent-first");
        String secondElder = elder(device.tenantId(), "concurrent-second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> calls = List.of(
                    bindCall(device, firstElder, ready, start),
                    bindCall(device, secondElder, ready, start));
            var futures = calls.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            List<Boolean> accepted = futures.stream().map(DeviceRegistryIntegrationTest::join).toList();
            assertThat(accepted).containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_binding WHERE device_id = ?",
                Long.class, device.id())).isEqualTo(1);
    }

    private Callable<Boolean> bindCall(
            DeviceSnapshot device, String elderId, CountDownLatch ready, CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                registry.bindElder(device.id(), elderId,
                        OBSERVED_AT.minusSeconds(60), null, "tester");
                return true;
            } catch (DeviceConflictException exception) {
                return false;
            }
        };
    }

    private void assertRejected(DeviceSnapshot device, String elderId, String eventId) {
        assertThatThrownBy(() -> riskHandler.handle(event(device, elderId, eventId)))
                .isInstanceOf(DeviceEventRejectedException.class);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM inbox_message
                         WHERE tenant_id = ? AND event_id = ?
                        """, Long.class, device.tenantId(), eventId)).isZero();
    }

    private DeviceSnapshot registeredDevice(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var product = registry.createProduct(
                "tenant-registry", "product-" + label + "-" + suffix, "Test product");
        return registry.register(
                "tenant-registry", "device-" + label + "-" + suffix, product.id(), "tester");
    }

    private String elder(String tenantId, String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return elderService.create(
                tenantId, "elder-" + label + "-" + suffix, "Test elder").id();
    }

    private RiskSourceEvent event(DeviceSnapshot device, String elderId, String eventId) {
        return new RiskSourceEvent(eventId, device.tenantId(), device.deviceKey(), elderId,
                AlarmSeverity.HIGH, OBSERVED_AT);
    }

    private static boolean join(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("concurrent binding call failed", exception);
        }
    }
}
