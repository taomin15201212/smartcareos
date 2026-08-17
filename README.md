# SmartCareOS 工程

SmartCareOS 是面向养老机构、社区、居家适老化和监管协作场景的智慧养老 IoT 平台。本工程依据仓库外层的《招聘信息汇总.md》启动，目前已交付设备注册、状态与有效期绑定，以及经设备资格校验的风险事件创建告警和护理处置。

## 已实现

- Java 21 与 Spring Boot 3.5 模块化单体骨架。
- 告警聚合、状态约束、升级级别和来源事件幂等。
- 创建、查询、确认、开始处理、解决、关闭和升级 REST API。
- JDBC 告警仓储、乐观锁和 Flyway 版本化迁移。
- 告警、状态轨迹与 Outbox 事件同事务落库。
- Outbox 租约式批量认领、失败退避、超时接管和成功留痕。
- 风险来源事件持久化、Inbox 消费幂等和载荷冲突检测。
- 风险来源事件到告警的事务链路及 REST 测试适配器。
- 设备产品、设备注册、激活/停用状态轨迹和老人有效期绑定 API。
- 基于事件发生时间的活跃设备与老人绑定校验。
- 通过设备行锁防止并发创建重叠绑定。
- 机构、房间、床位和老人主档 REST API。
- 入住、退住、床位占用状态和历史有效期冲突校验。
- 同人、同床并发入住串行化，设备绑定校验真实老人主档。
- 护理计划、护理任务、告警派单、任务轨迹和护理记录闭环。
- Broker 无关的 MQTT Topic/JSON 校验适配核心。
- Eclipse Paho MQTT QoS 1 持久会话订阅器、自动重连和 Broker 健康指示器。
- MQTT TLS 1.3 双向证书认证、PKCS12 客户端证书加载和证书身份 Topic ACL。
- RabbitMQ Topic Exchange 传输适配器、JSON 事件信封和发布确认门禁。
- 持久化联调队列、Dead Letter Exchange/DLQ、拒绝消息回灌和 Broker 重启恢复基线。
- API Key/OIDC 双认证边界、五类 RBAC（仅保存 API Key SHA-256 摘要）和数据库健康检查。
- 通知投递状态与政务交换任务/回执状态闭环。
- 请求前租户隔离、API 凭据轮换和追加式审计。
- Actuator 存活/就绪探针、Prometheus 指标和请求关联 ID。
- Docker/Compose 生产配置基线与 H2/MySQL 备份恢复工具。
- 默认 H2 文件数据库和可选本机 MySQL profile。
- 响应式机构运营工作台、租户汇总 API 和外部渠道沙箱适配器。
- 领域、API、数据库持久化与消息失败恢复集成测试。
- Knife4j/OpenAPI 在线接口文档，以及按九个系统模块组织的 46 个 HTTP 接口目录。

## 运行

本机未配置全局 Maven，可使用 IntelliJ 自带 Maven。

```bash
'/Users/keen/Applications/IntelliJ IDEA Ultimate.app/Contents/plugins/maven/lib/maven3/bin/mvn' -Dmaven.repo.local=.m2-repository test
'/Users/keen/Applications/IntelliJ IDEA Ultimate.app/Contents/plugins/maven/lib/maven3/bin/mvn' -Dmaven.repo.local=.m2-repository spring-boot:run
```

服务启动后访问 `GET /api/v1/system/health`。Knife4j 地址为
`http://localhost:8080/doc.html`；完整模块接口目录见 `docs/API-CATALOG.md`，详细请求示例见
`docs/API.md`。

本地默认开放接口文档，`production` profile 默认关闭。如确需在受控生产环境开放，设置
`SMARTCAREOS_API_DOCS_ENABLED=true`，并通过网关限制访问来源。

默认配置使用 `./data/smartcareos` H2 文件数据库，无需指定 profile：

```bash
java -jar target/smartcareos-1.0.0.jar
```

如需切换到本机 MySQL `smartcareos` 库，启用 `mysql` profile，并通过环境变量提供账号：

```bash
export SMARTCAREOS_DB_URL='jdbc:mysql://localhost:3306/smartcareos?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export SMARTCAREOS_DB_USERNAME=smartcareos
export SMARTCAREOS_DB_PASSWORD='replace-me'
SPRING_PROFILES_ACTIVE=mysql java -jar target/smartcareos-1.0.0.jar
```

自动化测试继续使用独立的 H2 内存数据库。`memory` profile 仍保留内存告警适配器，仅用于快速领域实验，不用于正式运行。H2 仅面向本地开发，上线前仍需在真实 MySQL 上完成验证。

### 本地观察 Outbox 发布

工程提供仅用于开发验证的日志传输适配器。启用后，后台任务会发布到日志并将事件标记为已完成：

```bash
SPRING_PROFILES_ACTIVE=outbox-log java -jar target/smartcareos-1.0.0.jar \
  --smartcareos.outbox.enabled=true
```

该适配器不等同于消息队列，不得作为生产传输方案。详细语义和配置见 `docs/OUTBOX.md`。

### 外部中间件联调

隔离联调使用 `compose.production.yml + compose.integration.yml`，不会占用默认的 MySQL、RabbitMQ 和 MQTT 端口：

```bash
cp .env.integration.example .env.integration
set -a; source .env.integration; set +a
./scripts/generate-mosquitto-certs.sh
docker compose --env-file .env.integration -p smartcareos-integration \
  -f compose.production.yml -f compose.integration.yml up -d mysql rabbitmq mosquitto
```

本地联调端口为 MySQL `13306`、RabbitMQ `5673/15673`、MQTT mTLS `18884`；应用建议使用 `8180/9190`。真实验证结果见 `docs/EXTERNAL-INTEGRATION-REPORT.md`、`docs/MESSAGING-RESILIENCE-REPORT.md` 和 `docs/MQTT-MTLS-REPORT.md`。

### 生产化准备

生产 profile、基础设施模板、凭据轮换、探针和备份恢复流程见 `docs/PRODUCTION-RUNBOOK.md`。生产 profile 强制从环境取得 MySQL 连接参数并开启 API Key 安全基线。

## 当前边界

当前已实现机构空间、老人、入住退住、设备注册绑定、风险事件、告警、护理、通知记录和政务交换的可运行数据库链路，以及 Outbox、Inbox、MQTT mTLS/设备 ACL、RabbitMQ 发布确认/DLQ、API Key/OIDC、RBAC、外部网关沙箱和机构运营工作台。微信/短信、政务端点和企业身份提供商的真实环境验收仍依赖第三方凭据；企业 PKI 证书撤销和完整业务前端仍需后续扩展。

## 事实与工程推演

- 来源事实：外层《招聘信息汇总.md》明确提及智慧养老、MQTT、MySQL、消息队列、机构后台、家属小程序和政务接口。
- 架构推演：模块化单体、风险来源事件、Inbox/Outbox、至少一次传输、载荷指纹、H2 开发数据库及后续拆分策略均为当前工程设计，需持续接受业务、性能和合规验证。
