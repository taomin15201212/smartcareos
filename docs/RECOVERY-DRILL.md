# M13 数据库恢复演练

`scripts/mysql-restore-drill.sh` 对隔离联调 MySQL 执行一致性导出，恢复到临时库 `smartcareos_restore_drill`，核对表数量和 Flyway 版本后删除临时库。脚本不覆盖主库。

本地演练目标：RPO 0（同一时点一致性快照），RTO 小于 15 分钟。生产 RPO/RTO 仍取决于备份频率、Binlog、对象存储和实际数据量。

## 来源事实与架构推演

- 来源事实：MySQL 来自招聘信息。
- 架构推演：一致性快照、临时库校验、RPO/RTO 门槛是生产化设计。
