package com.smartcareos.device.api;

import com.smartcareos.alarm.domain.AlarmSeverity;
import com.smartcareos.device.application.DeviceRiskEventHandler;
import com.smartcareos.device.domain.RiskSourceEvent;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/device-risk-events")
public class DeviceRiskEventController {

    private final DeviceRiskEventHandler handler;

    public DeviceRiskEventController(DeviceRiskEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @Operation(tags = "04 设备与 IoT", summary = "接收设备风险事件",
            description = "校验设备与老人绑定并幂等触发业务告警")
    ResponseEntity<DeviceRiskEventResponse> receive(@Valid @RequestBody DeviceRiskEventRequest request) {
        DeviceRiskEventHandler.HandleResult result = handler.handle(new RiskSourceEvent(
                request.eventId(),
                request.tenantId(),
                request.deviceId(),
                request.elderId(),
                request.severity(),
                request.observedAt()));
        HttpStatus status = result.outcome() == DeviceRiskEventHandler.Outcome.PROCESSED
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(new DeviceRiskEventResponse(
                request.eventId(),
                result.outcome(),
                result.alarm().id(),
                result.alarm().status().name()));
    }

    record DeviceRiskEventRequest(
            @NotBlank @Size(max = 128) String eventId,
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 64) String deviceId,
            @NotBlank @Size(max = 64) String elderId,
            @NotNull AlarmSeverity severity,
            @NotNull Instant observedAt
    ) {
    }

    record DeviceRiskEventResponse(
            String eventId,
            DeviceRiskEventHandler.Outcome outcome,
            String alarmId,
            String alarmStatus
    ) {
    }
}
