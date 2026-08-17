# SmartCareOS 整体构建与运行报告

> 最后更新：2026-08-17 11:27 CST

## 结论

M0–M16 内部工程范围已经闭环。默认 H2 与隔离 MySQL/mTLS 实例均运行 Schema V10；RBAC/OIDC 边界、性能、恢复、外部渠道沙箱和运营工作台已完成验证。

## 构建证据

- 工具：Java 21.0.11、Spring Boot 3.5.5、Maven。
- 命令：`mvn -Dmaven.repo.local=.m2-repository package`。
- 结果：`BUILD SUCCESS`，39 tests，0 failures，0 errors，0 skipped。
- 制品：`target/smartcareos-1.0.0.jar`，约 39 MB。
- 数据库：22 张业务/技术表，10 个 Flyway 版本。

## 实际运行状态

| 项目 | 值 |
|---|---|
| PID | 以当前运行进程为准 |
| HTTP | `http://127.0.0.1:8080` |
| 管理端口 | `http://127.0.0.1:9090` |
| 健康端点 | `GET /api/v1/system/health` |
| 状态 | `UP` |
| 数据库 | `data/smartcareos.mv.db` |
| Schema | Flyway `10` |
| V9/V10 升级前备份 | `backups/smartcareos-20260817T032024Z.mv.db` |

外部集成实例另运行于 HTTP `8180`、管理端口 `9190`，连接 MySQL `13306`、RabbitMQ `5673/15673` 和 MQTT mTLS `18884`；总体 Actuator 状态为 `UP`，Flyway Schema 为 `10`。mTLS 证据见 `docs/MQTT-MTLS-REPORT.md`。

## 冒烟测试

真实 HTTP 请求完成了健康检查、运营汇总和通知网关 `PENDING -> SENT`；沙箱生成外部流水号。性能实测为 200 请求、并发 10、P95 156.16 ms。MySQL 临时库恢复实测 RPO 0、RTO 4 秒。

## 表清单

`alarm`、`alarm_transition`、`outbox_event`、`inbox_message`、`device_risk_event`、`device_product`、`device`、`device_status_history`、`device_binding`、`institution`、`institution_room`、`institution_bed`、`elder`、`admission`、`care_plan`、`care_task`、`care_task_transition`、`care_record`、`api_credential`、`audit_event`、`notification_delivery`、`government_exchange_task`。

## 来源事实与架构推演

- 来源事实：智慧养老、MQTT 设备接入、机构端、家属小程序、政务监管接口、MySQL 和消息队列来自《招聘信息汇总.md》。
- 架构推演：模块化单体、DDD 上下文、Inbox/Outbox、护理状态机、API Key、通知投递和政务交换状态机为本工程设计。

## 外部残余项

仍未声称已完成：企业 PKI 证书撤销、微信/短信/政务真实环境验收、企业身份提供商实联、HTTP TLS/WAF 和秘密管理器。上述事项需要外部基础设施、凭据或正式契约。
