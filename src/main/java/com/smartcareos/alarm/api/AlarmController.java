package com.smartcareos.alarm.api;

import com.smartcareos.alarm.application.AlarmApplicationService;
import com.smartcareos.alarm.application.AlarmSnapshot;
import com.smartcareos.alarm.domain.AlarmSeverity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarms")
@Tag(name = "05 告警中心", description = "风险告警创建、查询、确认、处置、关闭与升级")
public class AlarmController {

    private final AlarmApplicationService applicationService;

    public AlarmController(AlarmApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @Operation(summary = "创建告警", description = "按来源事件幂等创建业务告警；重复事件返回既有告警")
    ResponseEntity<AlarmSnapshot> create(@Valid @RequestBody CreateAlarmRequest request) {
        AlarmApplicationService.CreateResult result = applicationService.create(
                new AlarmApplicationService.CreateCommand(
                        request.tenantId(),
                        request.elderId(),
                        request.sourceEventId(),
                        request.severity()
                )
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.alarm());
    }

    @GetMapping("/{alarmId}")
    @Operation(summary = "查询告警详情")
    AlarmSnapshot get(@PathVariable String alarmId) {
        return applicationService.get(alarmId);
    }

    @PostMapping("/{alarmId}/acknowledge")
    @Operation(summary = "确认告警")
    AlarmSnapshot acknowledge(@PathVariable String alarmId, @Valid @RequestBody ActorRequest request) {
        return applicationService.acknowledge(alarmId, request.actorId());
    }

    @PostMapping("/{alarmId}/start")
    @Operation(summary = "开始处置告警")
    AlarmSnapshot start(@PathVariable String alarmId, @Valid @RequestBody ActorRequest request) {
        return applicationService.start(alarmId, request.actorId());
    }

    @PostMapping("/{alarmId}/resolve")
    @Operation(summary = "解决告警")
    AlarmSnapshot resolve(@PathVariable String alarmId, @Valid @RequestBody ActorRequest request) {
        return applicationService.resolve(alarmId, request.actorId());
    }

    @PostMapping("/{alarmId}/close")
    @Operation(summary = "关闭告警")
    AlarmSnapshot close(@PathVariable String alarmId, @Valid @RequestBody ActorRequest request) {
        return applicationService.close(alarmId, request.actorId());
    }

    @PostMapping("/{alarmId}/escalate")
    @Operation(summary = "升级告警级别")
    AlarmSnapshot escalate(@PathVariable String alarmId, @Valid @RequestBody ActorRequest request) {
        return applicationService.escalate(alarmId, request.actorId());
    }

    record CreateAlarmRequest(
            @NotBlank String tenantId,
            @NotBlank String elderId,
            @NotBlank String sourceEventId,
            @NotNull AlarmSeverity severity
    ) {
    }

    record ActorRequest(@NotBlank String actorId) {
    }
}
