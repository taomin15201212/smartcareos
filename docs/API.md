# 设备、风险事件与告警 API

## 平台约定

`GET /api/v1/system/health` 不需认证。启用 `secure` profile 后，默认 API Key 模式要求 `X-SmartCare-Tenant`、`X-SmartCare-Principal` 和 `X-SmartCare-Api-Key`。OIDC 模式使用 Bearer JWT，并映射 `sub`、`tenant_id`、`roles` 声明。

安全模式会在业务处理前校验请求体和 URI 资源的租户归属。租户不匹配返回 `403 TENANT_ACCESS_DENIED`。每个响应包含 `X-Request-Id`。

### API 凭据轮换

- `POST /api/v1/api-credentials`：签发新凭据，需当前凭据具有管理能力。
- `DELETE /api/v1/api-credentials/{credentialId}`：撤销本租户凭据。

签发请求示例：

```json
{
  "tenantId": "institution-001",
  "principalId": "integration-service",
  "expiresAt": "2026-12-31T00:00:00Z",
  "role": "AUDITOR"
}
```

响应中的 `apiKey` 只显示一次，数据库只保留摘要。

## 机构、房间与床位

| 能力 | 请求 |
|---|---|
| 创建机构 | `POST /api/v1/institutions` |
| 创建房间 | `POST /api/v1/institutions/{institutionId}/rooms` |
| 创建床位 | `POST /api/v1/rooms/{roomId}/beds` |
| 查询床位 | `GET /api/v1/beds/{bedId}` |

创建机构：

```json
{"tenantId":"institution-001","institutionCode":"home-001","name":"康养中心"}
```

创建房间的请求体为 `{"roomCode":"101","name":"101 房"}`，创建床位为 `{"bedCode":"A"}`。

## 老人与入住

### 创建和查询老人

`POST /api/v1/elders`

```json
{"tenantId":"institution-001","elderNo":"elder-001","name":"陈某"}
```

查询使用 `GET /api/v1/elders/{elderId}`。

### 入住和退住

`POST /api/v1/admissions`

```json
{
  "tenantId": "institution-001",
  "elderId": "<elder-id>",
  "bedId": "<bed-id>",
  "admittedAt": "2026-08-15T08:00:00Z",
  "actorId": "staff-001"
}
```

查询使用 `GET /api/v1/admissions/{admissionId}`。退住使用：

`POST /api/v1/admissions/{admissionId}/discharge`

```json
{"dischargedAt":"2026-08-15T18:00:00Z","actorId":"staff-001"}
```

同一老人或同一床位的入住有效期不得重叠。业务状态冲突返回 `409 BUSINESS_STATE_CONFLICT`。

## 设备产品与设备

### 创建设备产品

`POST /api/v1/device-products`

```json
{"tenantId":"institution-001","productKey":"emergency-button","name":"紧急按钮"}
```

### 注册与查询设备

`POST /api/v1/devices`

```json
{
  "tenantId": "institution-001",
  "deviceKey": "emergency-button-001",
  "productId": "<device-product-id>",
  "actorId": "staff-001"
}
```

注册后状态为 `REGISTERED`。查询使用 `GET /api/v1/devices/{deviceId}`。

### 设备状态动作

| 动作 | 请求 |
|---|---|
| 激活 | `POST /api/v1/devices/{deviceId}/activate` |
| 停用 | `POST /api/v1/devices/{deviceId}/disable` |

请求体为 `{"actorId":"staff-001"}`。当前状态机为 `REGISTERED -> ACTIVE -> DISABLED`，不支持已停用设备直接重新激活。

### 绑定老人

`POST /api/v1/devices/{deviceId}/bindings`

```json
{
  "elderId": "elder-001",
  "validFrom": "2026-08-15T00:00:00Z",
  "validTo": null,
  "createdBy": "staff-001"
}
```

绑定前会校验老人主档存在、处于活跃状态并属于设备租户。绑定采用左闭右开有效期，同一设备的有效期不得重叠。结束开放绑定使用：

`DELETE /api/v1/devices/{deviceId}/bindings/{bindingId}?validTo=2026-08-16T00:00:00Z`

## 接收风险来源事件

`POST /api/v1/device-risk-events`

```json
{
  "eventId": "button-event-001",
  "tenantId": "institution-001",
  "deviceId": "emergency-button-001",
  "elderId": "elder-001",
  "severity": "HIGH",
  "observedAt": "2026-08-15T00:00:00Z"
}
```

首次处理返回 `202 Accepted` 和 `PROCESSED`，相同载荷重复提交返回原告警及 `DUPLICATE`。同一租户中，相同 `eventId` 若携带不同载荷，返回 `409 EVENT_PAYLOAD_CONFLICT`。设备未激活，或在 `observedAt` 时刻没有与 `elderId` 匹配的绑定时，返回 `422 DEVICE_EVENT_REJECTED`，且不保留 Inbox 记录。

此接口是当前设备风险链路的可测试适配器，不代表最终 MQTT Topic 或设备协议契约。

## 创建告警

`POST /api/v1/alarms`

```json
{
  "tenantId": "institution-001",
  "elderId": "elder-001",
  "sourceEventId": "device-event-001",
  "severity": "HIGH"
}
```

同一租户与 `sourceEventId` 重复提交时返回原告警，不创建重复记录。

直接创建告警接口主要保留给告警域测试和受控内部调用；设备接入应优先提交风险来源事件。

## 状态动作

| 动作 | 请求 |
|---|---|
| 确认 | `POST /api/v1/alarms/{id}/acknowledge` |
| 开始处理 | `POST /api/v1/alarms/{id}/start` |
| 解决 | `POST /api/v1/alarms/{id}/resolve` |
| 关闭 | `POST /api/v1/alarms/{id}/close` |
| 升级 | `POST /api/v1/alarms/{id}/escalate` |

状态动作请求体均为 `{"actorId":"staff-001"}`。升级可在告警关闭前发生，并只增加 `escalationLevel`，不改变当前处理状态。

## 护理与告警派单

| 能力 | 请求 |
|---|---|
| 创建/激活计划 | `POST /api/v1/care-plans`, `POST /api/v1/care-plans/{id}/activate` |
| 从计划生成任务 | `POST /api/v1/care-plans/{id}/tasks` |
| 告警幂等派单 | `POST /api/v1/alarms/{id}/care-task` |
| 开始/完成/取消 | `POST /api/v1/care-tasks/{id}/start|complete|cancel` |

计划初始为 `DRAFT`；仅 `ACTIVE` 计划可创建任务。任务流程为 `PENDING -> IN_PROGRESS -> COMPLETED`，完成时必须提交 `resultSummary`。

## 通知与政务交换

- 通知：`POST/GET /api/v1/notification-deliveries`，并通过 `/{id}/sent` 或 `/{id}/failed` 记录通道结果。
- 政务：`POST/GET /api/v1/government-exchanges`，通过 `/{id}/submit` 提交，`/{id}/receipt` 记录接受或拒绝回执。
- 沙箱/网关调度：通知和政务资源均支持 `POST /{id}/dispatch`。
- 运营汇总：`GET /api/v1/dashboard/summary`。

上述接口实现内部业务状态，不代表已连接微信、短信或政务真实端点。
