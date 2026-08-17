package com.smartcareos.device.api;

import com.smartcareos.device.application.DeviceRegistryService;
import com.smartcareos.device.application.DeviceRegistryStore;
import com.smartcareos.device.application.DeviceSnapshot;
import com.smartcareos.device.domain.DeviceBinding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "04 设备与 IoT", description = "设备产品、设备身份、生命周期与老人绑定")
public class DeviceRegistryController {

    private final DeviceRegistryService service;

    public DeviceRegistryController(DeviceRegistryService service) {
        this.service = service;
    }

    @PostMapping("/device-products")
    @Operation(summary = "创建设备产品")
    ResponseEntity<DeviceRegistryStore.DeviceProductSnapshot> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(
                request.tenantId(), request.productKey(), request.name()));
    }

    @PostMapping("/devices")
    @Operation(summary = "注册设备")
    ResponseEntity<DeviceSnapshot> register(@Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(
                request.tenantId(), request.deviceKey(), request.productId(), request.actorId()));
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(summary = "查询设备详情")
    DeviceSnapshot get(@PathVariable String deviceId) {
        return service.get(deviceId);
    }

    @PostMapping("/devices/{deviceId}/activate")
    @Operation(summary = "激活设备")
    DeviceSnapshot activate(
            @PathVariable String deviceId, @Valid @RequestBody ActorRequest request
    ) {
        return service.activate(deviceId, request.actorId());
    }

    @PostMapping("/devices/{deviceId}/disable")
    @Operation(summary = "停用设备")
    DeviceSnapshot disable(
            @PathVariable String deviceId, @Valid @RequestBody ActorRequest request
    ) {
        return service.disable(deviceId, request.actorId());
    }

    @PostMapping("/devices/{deviceId}/bindings")
    @Operation(summary = "绑定设备与老人", description = "绑定关系具有生效起止时间")
    ResponseEntity<DeviceBinding> bindElder(
            @PathVariable String deviceId, @Valid @RequestBody CreateBindingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.bindElder(
                deviceId, request.elderId(), request.validFrom(), request.validTo(),
                request.createdBy()));
    }

    @DeleteMapping("/devices/{deviceId}/bindings/{bindingId}")
    @Operation(summary = "结束设备绑定")
    DeviceBinding closeBinding(
            @PathVariable String deviceId,
            @PathVariable String bindingId,
            @RequestParam Instant validTo
    ) {
        return service.closeBinding(deviceId, bindingId, validTo);
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 64) String productKey,
            @NotBlank @Size(max = 128) String name
    ) {
    }

    record RegisterDeviceRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 64) String deviceKey,
            @NotBlank @Size(max = 36) String productId,
            @NotBlank @Size(max = 64) String actorId
    ) {
    }

    record ActorRequest(@NotBlank @Size(max = 64) String actorId) {
    }

    record CreateBindingRequest(
            @NotBlank @Size(max = 64) String elderId,
            @NotNull Instant validFrom,
            Instant validTo,
            @NotBlank @Size(max = 64) String createdBy
    ) {
    }
}
