package com.smartcareos.elder.api;

import com.smartcareos.elder.application.AdmissionService;
import com.smartcareos.elder.application.AdmissionSnapshot;
import com.smartcareos.elder.application.ElderService;
import com.smartcareos.elder.application.ElderStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "03 老人与入住", description = "老人主档、床位入住与离院管理")
public class ElderController {

    private final ElderService elderService;
    private final AdmissionService admissionService;

    public ElderController(ElderService elderService, AdmissionService admissionService) {
        this.elderService = elderService;
        this.admissionService = admissionService;
    }

    @PostMapping("/elders")
    @Operation(summary = "创建老人档案")
    ResponseEntity<ElderStore.ElderSnapshot> createElder(
            @Valid @RequestBody CreateElderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(elderService.create(
                request.tenantId(), request.elderNo(), request.name()));
    }

    @GetMapping("/elders/{elderId}")
    @Operation(summary = "查询老人档案")
    ElderStore.ElderSnapshot getElder(@PathVariable String elderId) {
        return elderService.get(elderId);
    }

    @PostMapping("/admissions")
    @Operation(summary = "办理入住", description = "建立老人与床位的有效入住关系")
    ResponseEntity<AdmissionSnapshot> admit(@Valid @RequestBody AdmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admissionService.admit(
                request.tenantId(), request.elderId(), request.bedId(), request.admittedAt(),
                request.actorId()));
    }

    @GetMapping("/admissions/{admissionId}")
    @Operation(summary = "查询入住记录")
    AdmissionSnapshot getAdmission(@PathVariable String admissionId) {
        return admissionService.get(admissionId);
    }

    @PostMapping("/admissions/{admissionId}/discharge")
    @Operation(summary = "办理离院")
    AdmissionSnapshot discharge(
            @PathVariable String admissionId,
            @Valid @RequestBody DischargeRequest request
    ) {
        return admissionService.discharge(
                admissionId, request.dischargedAt(), request.actorId());
    }

    record CreateElderRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 64) String elderNo,
            @NotBlank @Size(max = 128) String name
    ) {
    }

    record AdmitRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 36) String elderId,
            @NotBlank @Size(max = 36) String bedId,
            @NotNull Instant admittedAt,
            @NotBlank @Size(max = 64) String actorId
    ) {
    }

    record DischargeRequest(
            @NotNull Instant dischargedAt,
            @NotBlank @Size(max = 64) String actorId
    ) {
    }
}
