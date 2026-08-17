package com.smartcareos.care.api;

import com.smartcareos.care.application.CareService;
import com.smartcareos.care.application.CareStore;
import com.smartcareos.care.application.CareTaskSnapshot;
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
@Tag(name = "06 照护管理", description = "照护计划、护理任务及告警联动处置")
public class CareController {

    private final CareService service;

    public CareController(CareService service) {
        this.service = service;
    }

    @PostMapping("/care-plans")
    @Operation(summary = "创建照护计划")
    ResponseEntity<CareStore.CarePlanSnapshot> createPlan(
            @Valid @RequestBody CreatePlanRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPlan(
                request.tenantId(), request.elderId(), request.name(), request.scheduleRule()));
    }

    @PostMapping("/care-plans/{planId}/activate")
    @Operation(summary = "启用照护计划")
    CareStore.CarePlanSnapshot activatePlan(@PathVariable String planId) {
        return service.activatePlan(planId);
    }

    @PostMapping("/care-plans/{planId}/tasks")
    @Operation(summary = "从照护计划创建任务")
    ResponseEntity<CareTaskSnapshot> createPlanTask(
            @PathVariable String planId, @Valid @RequestBody CreateTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPlanTask(
                planId, request.title(), request.assigneeId(), request.dueAt(), request.actorId()));
    }

    @PostMapping("/alarms/{alarmId}/care-task")
    @Operation(summary = "从告警创建护理任务", description = "同一告警重复请求返回既有护理任务")
    ResponseEntity<CareTaskSnapshot> createAlarmTask(
            @PathVariable String alarmId, @Valid @RequestBody CreateTaskRequest request
    ) {
        CareStore.SaveTaskResult result = service.createAlarmTask(
                alarmId, request.title(), request.assigneeId(), request.dueAt(), request.actorId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CareTaskSnapshot.from(result.task()));
    }

    @GetMapping("/care-tasks/{taskId}")
    @Operation(summary = "查询护理任务")
    CareTaskSnapshot getTask(@PathVariable String taskId) {
        return service.getTask(taskId);
    }

    @PostMapping("/care-tasks/{taskId}/start")
    @Operation(summary = "开始执行护理任务")
    CareTaskSnapshot start(
            @PathVariable String taskId, @Valid @RequestBody ActorRequest request
    ) {
        return service.start(taskId, request.actorId());
    }

    @PostMapping("/care-tasks/{taskId}/complete")
    @Operation(summary = "完成护理任务")
    CareTaskSnapshot complete(
            @PathVariable String taskId, @Valid @RequestBody CompleteTaskRequest request
    ) {
        return service.complete(taskId, request.actorId(), request.resultSummary());
    }

    @PostMapping("/care-tasks/{taskId}/cancel")
    @Operation(summary = "取消护理任务")
    CareTaskSnapshot cancel(
            @PathVariable String taskId, @Valid @RequestBody ActorRequest request
    ) {
        return service.cancel(taskId, request.actorId());
    }

    record CreatePlanRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 36) String elderId,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 128) String scheduleRule
    ) {
    }

    record CreateTaskRequest(
            @NotBlank @Size(max = 128) String title,
            @NotBlank @Size(max = 64) String assigneeId,
            @NotNull Instant dueAt,
            @NotBlank @Size(max = 64) String actorId
    ) {
    }

    record ActorRequest(@NotBlank @Size(max = 64) String actorId) {
    }

    record CompleteTaskRequest(
            @NotBlank @Size(max = 64) String actorId,
            @NotBlank @Size(max = 512) String resultSummary
    ) {
    }
}
