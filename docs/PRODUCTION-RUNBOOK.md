# SmartCareOS 生产化运行手册

## 不可变制品切换

不要覆盖正在运行的 JAR。Java 启动器会按需从归档加载类，原地替换可能在停机或运行中触发类加载失败。发布时把新版本复制为独立、带版本的只读文件，完成校验后启动新进程并切换流量；确认健康后再停止旧进程。保留上一版本以便回滚。

## 来源事实

《招聘信息汇总.md》支撑 MySQL、MQTT、消息队列、机构端和政务接口的技术/业务边界，但未指定生产云、Broker 产品、SLA 或备份周期。

## 架构推演

### 发布前门禁

1. `mvn package` 全部测试通过，Flyway 迁移在脱敏 MySQL 副本验证。
2. 配置 `production` profile 所需的数据库变量，秘密不写入镜像、YAML 或日志。
3. 首次启动才传入 bootstrap API Key，立即签发日常管理凭据并从环境移除 bootstrap 秘密。
4. 执行数据库备份、记录校验和，验证回滚制品可用。
5. 仅在健康、就绪、指标和核心冒烟测试成功后切流。

### 运行

```bash
cp .env.production.example .env.production
# 将值改为秘密管理器或受控环境注入的值
set -a; source .env.production; set +a
./scripts/generate-mosquitto-certs.sh
docker compose --env-file .env.production -f compose.production.yml up -d mysql rabbitmq mosquitto
mvn package
docker compose --env-file .env.production -f compose.production.yml --profile app up -d --build
```

Compose 中应用默认激活 `production,rabbitmq`；若直接运行 JAR，也必须显式激活 `rabbitmq` 才会使用真实发布适配器。MQTT 由 `SMARTCAREOS_MQTT_ENABLED=true` 开启，Broker 仅监听 TLS `8883`，应用必须提供 PKCS12 trust store 和 key store。

`generate-mosquitto-certs.sh` 生成的本地 CA 仅用于隔离联调。生产环境必须由企业 PKI/设备 CA 签发，设置证书有效期、撤销/轮换和私钥托管策略；不得把 `deploy/mosquitto/certs` 纳入镜像或源码包。

脚本默认拒绝覆盖已有证书。只有完成备份、影响分析和回滚准备后，才可显式设置 `SMARTCAREOS_MQTT_ROTATE_CERTS=true` 执行整体轮换；轮换后必须同步重启 Broker/应用并重新验证所有设备证书。

- 业务健康：`GET http://127.0.0.1:8080/api/v1/system/health`
- Kubernetes 风格探针：`GET http://127.0.0.1:9090/actuator/health/liveness` 和 `/readiness`
- Prometheus：`GET http://127.0.0.1:9090/actuator/prometheus`
- 每个响应带 `X-Request-Id`，可与日志和 `audit_event` 关联。

### 凭据轮换

`POST /api/v1/api-credentials` 仅接受具有凭据管理能力的当前凭据。响应中 `apiKey` 只返回一次；调用方验证新 Key 后，通过 `DELETE /api/v1/api-credentials/{id}` 撤销旧凭据。

### 备份与恢复

- H2：停服后执行 `scripts/backup-h2.sh`；脚本会拒绝复制正在打开的数据文件。
- MySQL：配置 `SMARTCAREOS_DB_HOST/USERNAME/PASSWORD`，执行 `scripts/backup-mysql.sh`。
- 恢复必须显式使用 `--confirm`，且先在隔离环境演练。RPO/RTO 需业务方确认后再设置调度周期。

### 故障处理

- 数据库不就绪：停止切流，保留现场，检查 Flyway 历史和连接池指标；不得手工跳过迁移。
- Outbox 积压：检查失败原因与下次重试时间，保持至少一次语义，不直接删除事件。
- MQTT 不就绪：检查证书链、SAN/主机名、有效期、Client ID 和 Topic ACL；不要关闭主机名校验、客户端证书或匿名访问门禁来规避故障。
- RabbitMQ NACK/超时：保留 Outbox 重试状态，检查 Exchange/队列绑定和 publisher confirms，不手工设置 `published_at`。
- 认证异常：检索 `audit_event` 的 401/403，确认凭据状态、过期时间和租户边界，不打印原始 Key。

### RabbitMQ DLQ 处置

1. 暂停对应消费者，记录 DLQ 消息的 `message_id`、`eventId`、`x-death.reason` 和原 routing key。
2. 修复可重复的消费失败原因；先在隔离队列验证同一事件。
3. 先向原 Exchange 发布完整原始信封并确认 `routed=true`，再 ACK 移除 DLQ 原消息。
4. 消费者必须按 `eventId` 幂等；禁止先删除 DLQ、后尝试回灌。
5. 回灌后验证主队列、DLQ、Inbox 和业务状态，记录审计证据。

联调参考拓扑与真实演练证据见 `MESSAGING-RESILIENCE-REPORT.md`。
