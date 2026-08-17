# Changelog

## 1.0.0 - 2026-08-17

- 增加 API Key/OIDC 双认证边界、五类 RBAC 和 SQL 租户约束。
- Flyway V9–V10 增加凭据角色与外部网关状态字段。
- 增加通知/政务统一外部网关、本地沙箱与 HTTP 适配模式。
- 交付响应式机构运营工作台与租户级运营汇总 API。
- 完成 200 请求/并发 10 性能基线，P95 156.16 ms。
- 完成 MySQL 临时库恢复演练，RPO 0、实测 RTO 4 秒。
- H2 与 MySQL 实例均升级至 V10；39 项测试全部通过。

## 0.2.0-SNAPSHOT - 2026-08-17

- 新增 Eclipse Paho MQTT 真实 Broker 订阅器：QoS 1、持久会话、自动重连、健康指示器。
- 新增 RabbitMQ Outbox 传输：Topic Exchange、标准 JSON 信封、CorrelationData 发布确认及 NACK 失败重试语义。
- 新增隔离联调 Compose 覆盖文件和认证 Mosquitto 健康检查。
- RabbitMQ 健康探针限定在 `rabbitmq` profile，避免默认 H2 模式误报中间件故障。
- MySQL 8.4 实际执行 Flyway V1–V8；真实完成 MQTT → 告警 → Outbox → RabbitMQ 链路及重复事件幂等验证。
- 全量构建 36 个测试通过，0 failures、0 errors、0 skipped。

## 0.3.0-SNAPSHOT - 2026-08-17

- 联调队列升级为持久化 `smartcare.events.integration.v2`。
- 新增持久化 Dead Letter Exchange `smartcare.events.dlx` 与 DLQ。
- 增加拓扑自动化测试，校验队列持久化和死信路由参数。
- 真实完成消息拒绝、`x-death` 证据检查、受控回灌和 RabbitMQ 容器重启恢复演练。
- Broker 重启后新 MQTT 风险事件继续发布成功；全量 37 个测试通过。

## 0.4.0-SNAPSHOT - 2026-08-17

- Mosquitto 接入切换为 TLS `8883`，强制客户端证书并以证书 CN 作为授权身份。
- Paho 订阅器增加 PKCS12 trust store/key store 加载和服务端主机名校验。
- 增加应用只读身份与设备单 Topic 写身份 ACL，以及本地联调证书生成脚本。
- 证书生成脚本默认拒绝覆盖既有 CA/证书，整体轮换必须显式授权。
- 真实验证 TLS 1.3、无证书握手拒绝、应用证书越权发布 `Not authorized`、设备证书正向链路和 Broker 重启自动重连。
- 私钥、证书库和口令均排除在版本资产与 ZIP 交付包之外；全量 37 个测试通过。

## 0.1.0-SNAPSHOT - 2026-08-16

- 交付告警、机构、老人、入住、设备、风险事件与护理后端纵向切片。
- 增加 Inbox/Outbox、Flyway V1–V7 及 H2/MySQL profile。
- 增加 MQTT 契约适配核心、API Key 认证、健康检查。
- 增加通知投递和政务交换状态闭环。
- 34 个自动化测试通过，默认 H2 文件库实际升级到 V8。
- 生产化基线：Flyway V8、请求前租户隔离、API 凭据轮换、追加式审计、Actuator/Prometheus、Docker Compose 及备份恢复脚本。
