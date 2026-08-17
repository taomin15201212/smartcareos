# SmartCareOS 1.0.0 生产验收报告

> 验收时间：2026-08-17 11:26 CST

## 结论

M11–M16 的内部工程范围已完成：身份与角色、租户隔离、性能基线、数据库恢复、外部渠道沙箱、前端 MVP 和发布验收均有可运行实现与验证证据。微信、短信、政务和企业 OIDC 的第三方环境验收不在无凭据环境中虚构完成。

## 验收矩阵

| 门禁 | 结果 | 证据 |
|---|---|---|
| 自动化测试 | PASS | 39 tests，0 failure/error/skip |
| H2 运行 | PASS | HTTP 8080 / 管理 9090 / Schema V10 |
| MySQL 运行 | PASS | HTTP 8180 / 管理 9190 / Schema V10 |
| MQTT/RabbitMQ | PASS | mTLS 订阅已连接，管理健康状态 UP |
| RBAC/OIDC 边界 | PASS | API Key、OIDC Token 正负向测试 |
| 性能基线 | PASS | 200 请求、并发 10、P95 156.16 ms、0 错误 |
| 恢复演练 | PASS | 23 张含 Schema History 表，RPO 0、RTO 4 秒 |
| 前端 MVP | PASS | 根地址工作台与租户汇总 API |
| 外部渠道 | SANDBOX PASS | MySQL 实例通知 `PENDING -> SENT`，生成沙箱流水号 |

## 已知边界

- MySQL 8.4 高于当前 Flyway 明示测试上限 8.1；迁移和运行已验证，但升级 Flyway 后需重新回归。
- 生产容量需要在目标硬件、网络和脱敏业务数据上复测。
- 企业 OIDC、微信、短信和政务真实环境仍需要 Issuer/JWKS、应用凭据、签名加密规则、白名单和正式契约。

## 来源事实与架构推演

- 来源事实：智慧养老、机构后台、家属小程序、MQTT、MySQL、消息队列和政务接口来自《招聘信息汇总.md》。
- 架构推演：M11–M16 验收门槛、RBAC 角色、OIDC 声明、沙箱网关、性能与 RPO/RTO 目标均为本工程设计。
