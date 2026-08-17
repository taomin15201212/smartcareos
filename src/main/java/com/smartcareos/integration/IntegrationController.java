package com.smartcareos.integration;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IntegrationController {
    private final NotificationService notifications; private final GovernmentExchangeService government;
    public IntegrationController(NotificationService n,GovernmentExchangeService g){notifications=n;government=g;}
    @PostMapping("/notification-deliveries")
    @Operation(tags = "07 通知中心", summary = "创建通知投递任务")
    Map<String,Object> createNotification(@Valid @RequestBody NotificationRequest r){return notifications.create(r.tenantId,r.businessType,r.businessId,r.channel,r.recipient,r.summary);}
    @GetMapping("/notification-deliveries/{id}")
    @Operation(tags = "07 通知中心", summary = "查询通知投递任务")
    Map<String,Object> getNotification(@PathVariable String id){return notifications.get(id);}
    @PostMapping("/notification-deliveries/{id}/sent")
    @Operation(tags = "07 通知中心", summary = "标记通知发送成功")
    Map<String,Object> sent(@PathVariable String id){return notifications.finish(id,true,null);}
    @PostMapping("/notification-deliveries/{id}/dispatch")
    @Operation(tags = "07 通知中心", summary = "派发通知", description = "调用已配置的通知通道适配器")
    Map<String,Object> dispatchNotification(@PathVariable String id){return notifications.dispatch(id);}
    @PostMapping("/notification-deliveries/{id}/failed")
    @Operation(tags = "07 通知中心", summary = "标记通知发送失败")
    Map<String,Object> failed(@PathVariable String id,@RequestBody Failure r){return notifications.finish(id,false,r.error);}
    @PostMapping("/government-exchanges")
    @Operation(tags = "08 政务交换", summary = "创建政务交换任务")
    Map<String,Object> createExchange(@Valid @RequestBody ExchangeRequest r){return government.create(r.tenantId,r.contractCode,r.mappingVersion,r.periodStart,r.periodEnd,r.payload);}
    @GetMapping("/government-exchanges/{id}")
    @Operation(tags = "08 政务交换", summary = "查询政务交换任务")
    Map<String,Object> getExchange(@PathVariable String id){return government.get(id);}
    @PostMapping("/government-exchanges/{id}/submit")
    @Operation(tags = "08 政务交换", summary = "提交政务交换任务")
    Map<String,Object> submit(@PathVariable String id){return government.submit(id);}
    @PostMapping("/government-exchanges/{id}/dispatch")
    @Operation(tags = "08 政务交换", summary = "派发政务交换数据", description = "调用已配置的政务 HTTP 适配器")
    Map<String,Object> dispatchGovernment(@PathVariable String id){return government.dispatch(id);}
    @PostMapping("/government-exchanges/{id}/receipt")
    @Operation(tags = "08 政务交换", summary = "登记政务交换回执")
    Map<String,Object> receipt(@PathVariable String id,@Valid @RequestBody Receipt r){return government.receipt(id,r.accepted,r.externalReceipt,r.message);}
    record NotificationRequest(@NotBlank String tenantId,@NotBlank String businessType,@NotBlank String businessId,@NotBlank String channel,@NotBlank String recipient,@NotBlank String summary){}
    record Failure(@NotBlank String error){}
    record ExchangeRequest(@NotBlank String tenantId,@NotBlank String contractCode,@NotBlank String mappingVersion,@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd,@NotBlank String payload){}
    record Receipt(boolean accepted,@NotBlank String externalReceipt,String message){}
}
