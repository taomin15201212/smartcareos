package com.smartcareos.elder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.elder.application.AdmissionService;
import com.smartcareos.elder.application.AdmissionSnapshot;
import com.smartcareos.elder.application.ElderConflictException;
import com.smartcareos.elder.application.ElderService;
import com.smartcareos.elder.application.ElderStore;
import com.smartcareos.institution.application.InstitutionConflictException;
import com.smartcareos.institution.application.InstitutionService;
import com.smartcareos.institution.application.InstitutionStore;
import com.smartcareos.institution.domain.BedStatus;
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
        "spring.datasource.url=jdbc:h2:mem:smartcareos_admission_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdmissionIntegrationTest {

    private static final Instant ADMITTED_AT = Instant.parse("2026-08-15T08:00:00Z");
    private static final Instant DISCHARGED_AT = Instant.parse("2026-08-15T18:00:00Z");

    @Autowired InstitutionService institutionService;
    @Autowired ElderService elderService;
    @Autowired AdmissionService admissionService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void exposesInstitutionElderAdmissionAndDischargeApis() throws Exception {
        String suffix = suffix();
        String institutionJson = mockMvc.perform(post("/api/v1/institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"tenant-api","institutionCode":"inst-%s",\
                                "name":"SmartCare Home"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String institutionId = objectMapper.readTree(institutionJson).get("id").asText();

        String roomJson = mockMvc.perform(post("/api/v1/institutions/{id}/rooms", institutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomCode\":\"101\",\"name\":\"Room 101\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String roomId = objectMapper.readTree(roomJson).get("id").asText();

        String bedJson = mockMvc.perform(post("/api/v1/rooms/{id}/beds", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bedCode\":\"A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        String bedId = objectMapper.readTree(bedJson).get("id").asText();

        String elderJson = mockMvc.perform(post("/api/v1/elders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"tenant-api","elderNo":"elder-%s","name":"Chen"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String elderId = objectMapper.readTree(elderJson).get("id").asText();

        String admissionJson = mockMvc.perform(post("/api/v1/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"tenant-api","elderId":"%s","bedId":"%s",\
                                "admittedAt":"2026-08-15T08:00:00Z","actorId":"staff-api"}
                                """.formatted(elderId, bedId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionId").value(institutionId))
                .andExpect(jsonPath("$.dischargedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String admissionId = objectMapper.readTree(admissionJson).get("id").asText();

        mockMvc.perform(post("/api/v1/admissions/{id}/discharge", admissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dischargedAt":"2026-08-15T18:00:00Z","actorId":"staff-api"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dischargedBy").value("staff-api"));

        assertThat(institutionService.getBed(bedId).status()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void preventsOneElderFromHoldingTwoOpenAdmissions() {
        BedFixture first = bed("tenant-elder-conflict", "first");
        BedFixture second = anotherBed(first.institutionId(), "tenant-elder-conflict", "second");
        ElderStore.ElderSnapshot elder = elder("tenant-elder-conflict", "one");
        admissionService.admit(first.tenantId(), elder.id(), first.bedId(), ADMITTED_AT, "tester");

        assertThatThrownBy(() -> admissionService.admit(
                second.tenantId(), elder.id(), second.bedId(), ADMITTED_AT, "tester"))
                .isInstanceOf(ElderConflictException.class)
                .hasMessageContaining("overlaps");
        assertThat(institutionService.getBed(second.bedId()).status())
                .isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void preventsOneBedFromHoldingTwoOpenAdmissions() {
        BedFixture bed = bed("tenant-bed-conflict", "shared");
        ElderStore.ElderSnapshot first = elder(bed.tenantId(), "first");
        ElderStore.ElderSnapshot second = elder(bed.tenantId(), "second");
        admissionService.admit(bed.tenantId(), first.id(), bed.bedId(), ADMITTED_AT, "tester");

        assertThatThrownBy(() -> admissionService.admit(
                bed.tenantId(), second.id(), bed.bedId(), ADMITTED_AT, "tester"))
                .isInstanceOf(InstitutionConflictException.class);
    }

    @Test
    void dischargeReleasesBedForAnotherElder() {
        BedFixture bed = bed("tenant-discharge", "reusable");
        ElderStore.ElderSnapshot first = elder(bed.tenantId(), "first");
        ElderStore.ElderSnapshot second = elder(bed.tenantId(), "second");
        AdmissionSnapshot firstAdmission = admissionService.admit(
                bed.tenantId(), first.id(), bed.bedId(), ADMITTED_AT, "tester");

        admissionService.discharge(firstAdmission.id(), DISCHARGED_AT, "tester");
        AdmissionSnapshot secondAdmission = admissionService.admit(
                bed.tenantId(), second.id(), bed.bedId(), DISCHARGED_AT, "tester");

        assertThat(secondAdmission.elderId()).isEqualTo(second.id());
        assertThat(institutionService.getBed(bed.bedId()).status()).isEqualTo(BedStatus.OCCUPIED);
    }

    @Test
    void rejectsBackdatedAdmissionThatOverlapsDischargedHistory() {
        BedFixture bed = bed("tenant-history", "history");
        ElderStore.ElderSnapshot first = elder(bed.tenantId(), "first");
        ElderStore.ElderSnapshot second = elder(bed.tenantId(), "second");
        AdmissionSnapshot admission = admissionService.admit(
                bed.tenantId(), first.id(), bed.bedId(), ADMITTED_AT, "tester");
        admissionService.discharge(admission.id(), DISCHARGED_AT, "tester");

        assertThatThrownBy(() -> admissionService.admit(
                bed.tenantId(), second.id(), bed.bedId(),
                ADMITTED_AT.plusSeconds(60), "tester"))
                .isInstanceOf(InstitutionConflictException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void serializesConcurrentAdmissionsToOneBed() throws Exception {
        BedFixture bed = bed("tenant-admission-race", "race");
        ElderStore.ElderSnapshot first = elder(bed.tenantId(), "first");
        ElderStore.ElderSnapshot second = elder(bed.tenantId(), "second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> calls = List.of(
                    admitCall(bed, first.id(), ready, start),
                    admitCall(bed, second.id(), ready, start));
            var futures = calls.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            assertThat(futures.stream().map(AdmissionIntegrationTest::join).toList())
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM admission
                         WHERE bed_id = ? AND discharged_at IS NULL
                        """, Long.class, bed.bedId())).isEqualTo(1);
    }

    @Test
    void serializesConcurrentAdmissionsForOneElder() throws Exception {
        BedFixture firstBed = bed("tenant-elder-race", "first");
        BedFixture secondBed = anotherBed(
                firstBed.institutionId(), firstBed.tenantId(), "second");
        ElderStore.ElderSnapshot elder = elder(firstBed.tenantId(), "race");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> calls = List.of(
                    admitCall(firstBed, elder.id(), ready, start),
                    admitCall(secondBed, elder.id(), ready, start));
            var futures = calls.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            assertThat(futures.stream().map(AdmissionIntegrationTest::join).toList())
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM admission
                         WHERE elder_id = ? AND discharged_at IS NULL
                        """, Long.class, elder.id())).isEqualTo(1);
    }

    @Test
    void rejectsCrossTenantAdmission() {
        BedFixture bed = bed("tenant-bed-owner", "cross-tenant");
        ElderStore.ElderSnapshot elder = elder("tenant-other", "cross-tenant");

        assertThatThrownBy(() -> admissionService.admit(
                "tenant-other", elder.id(), bed.bedId(), ADMITTED_AT, "tester"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bed not found");
    }

    private Callable<Boolean> admitCall(
            BedFixture bed, String elderId, CountDownLatch ready, CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                admissionService.admit(
                        bed.tenantId(), elderId, bed.bedId(), ADMITTED_AT, "tester");
                return true;
            } catch (InstitutionConflictException | ElderConflictException exception) {
                return false;
            }
        };
    }

    private BedFixture bed(String tenantId, String label) {
        String suffix = suffix();
        InstitutionStore.InstitutionSnapshot institution =
                institutionService.createInstitution(
                        tenantId, "institution-" + label + "-" + suffix, "Institution");
        return anotherBed(institution.id(), tenantId, label);
    }

    private BedFixture anotherBed(String institutionId, String tenantId, String label) {
        String suffix = suffix();
        InstitutionStore.RoomSnapshot room = institutionService.createRoom(
                institutionId, "room-" + label + "-" + suffix, "Room");
        InstitutionStore.BedSnapshot bed = institutionService.createBed(
                room.id(), "bed-" + label + "-" + suffix);
        return new BedFixture(tenantId, institutionId, bed.id());
    }

    private ElderStore.ElderSnapshot elder(String tenantId, String label) {
        String suffix = suffix();
        return elderService.create(tenantId, "elder-" + label + "-" + suffix, "Elder " + label);
    }

    private static boolean join(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("concurrent admission failed", exception);
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record BedFixture(String tenantId, String institutionId, String bedId) {
    }
}
