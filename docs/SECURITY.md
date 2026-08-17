# API 安全基线

## 来源事实

招聘信息表明系统包含机构端、家属端和政务对接，未给出具体身份协议。

## 架构推演

`secure` profile 使用租户 API Key 校验。数据库只存 SHA-256 摘要，支持状态和过期时间。原始 Key 由环境变量注入，不写入日志。

```bash
SMARTCAREOS_BOOTSTRAP_API_KEY='replace-me' SMARTCAREOS_BOOTSTRAP_TENANT='tenant-001' \
SPRING_PROFILES_ACTIVE=secure java -jar target/smartcareos-1.0.0.jar
```

安全模式会在进入控制器前校验路径资源和请求体的租户边界。管理型凭据可签发新 Key，原始 Key 仅在签发响应中出现一次；撤销后立即失效。变更请求和拒绝结果进入 `audit_event`。

生产仍需在入口实施 TLS/WAF，使用秘密管理器，完成终端用户 OIDC/RBAC、数据库层租户查询约束和审计存档策略。
