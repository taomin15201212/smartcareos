window.ARCH_DATA={
  business:{title:'智慧养老业务全景',subtitle:'从服务对象到能力中台，查看 SmartCareOS 的业务边界与价值链。',layers:[
    {label:'服务对象 · ACTORS',cols:4,nodes:[
      ['service-network','智慧养老服务网络','养老机构 · 社区 · 家庭 · 政府','⌘','fact','live'],['institution-portal','机构运营人员','运营管理与服务调度','▣','fact','live'],['family','老人及家属','安全知情与服务协同','⌂','fact','external'],['government','政府监管部门','监管数据交换与审计','◎','fact','external']]},
    {label:'价值流 · VALUE STREAM',cols:5,nodes:[
      ['admission','入住建档','档案 · 联系人 · 授权','01','inference','live'],['binding','设备绑定','注册 · 激活 · 时效绑定','02','inference','live'],['risk','风险感知','遥测 · 规则 · 风险事件','03','fact','live'],['response','告警处置','确认 · 处理 · 升级','04','inference','live'],['collaboration','协同闭环','家属通知 · 政务交换','05','inference','external']]},
    {label:'核心业务能力 · CAPABILITIES',cols:4,nodes:[
      ['institution','机构与空间','院区 · 房间 · 床位 · 人员','▤','inference','live'],['elder','老人与入住','档案 · 入住 · 联系人','◉','inference','live'],['device','设备与物模型','身份 · 绑定 · 影子 · 指令','⌁','fact','live'],['alarm','告警中心','识别 · 分级 · 升级 · 闭环','!','fact','live'],['care','照护管理','计划 · 任务 · 记录 · 交班','✚','inference','live'],['notification','通知中心','订阅 · 授权 · 投递结果','↗','inference','live'],['gov-adapter','政务交换','映射 · 任务 · 回执','⇄','fact','external'],['sales','产品与售后','订单 · 物流 · 售后协作','◇','fact','external']]},
    {label:'企业治理 · GOVERNANCE',cols:4,nodes:[
      ['identity','身份与授权','OIDC/API Key · RBAC','⊙','inference','live'],['tenant','多租户边界','HTTP + SQL 双重约束','⊞','inference','live'],['audit','审计追踪','敏感读取 · 状态 · 导出','◫','inference','live'],['observability','运行可观测','指标 · 健康 · 告警','∿','inference','live']]}
  ]},
  application:{title:'应用与领域架构',subtitle:'以八个限界上下文组织业务规则，当前由模块化单体承载。',layers:[
    {label:'体验层 · EXPERIENCE',cols:4,nodes:[['portal-app','机构管理门户','运营工作台 · 响应式 Web','▣','fact','live'],['nurse-app','护理工作端','移动任务 · 处置记录','✚','inference','external'],['family-app','家属微信小程序','授权查询 · 告警摘要','⌂','fact','external'],['gov-system','政务监管系统','版本化交换契约','◎','fact','external']]},
    {label:'应用层 · APPLICATION',cols:4,nodes:[['api','REST API','统一入口 · DTO · 校验','↔','fact','live'],['usecase','应用服务','用例编排 · 事务边界','⌘','inference','live'],['outbox','Outbox / Inbox','事务消息 · 消费幂等','⇢','inference','live'],['adapters','外部适配器','MQTT · AMQP · HTTP','⌁','inference','live']]},
    {label:'领域层 · BOUNDED CONTEXTS',cols:4,nodes:[['identity','Identity','身份 · 凭据 · 权限','ID','inference','live'],['institution','Institution','组织 · 空间 · 床位','IN','inference','live'],['elder','Elder','档案 · 入住 · 授权','EL','inference','live'],['device','Device','设备 · 绑定 · 物模型','DV','inference','live'],['alarm','Alarm','告警 · 级别 · 状态机','AL','inference','live'],['care','Care','计划 · 任务 · 记录','CA','inference','live'],['notification','Notification','订阅 · 内容 · 投递','NO','inference','live'],['gov-adapter','Government','数据映射 · 任务 · 回执','GV','inference','live']]},
    {label:'工程形态 · MODULAR MONOLITH',cols:3,nodes:[['spring','Spring Boot Runtime','单一部署 · 清晰模块边界','S','fact','live'],['ports','Ports & Adapters','领域不依赖基础设施','⬡','inference','live'],['events','Domain Events','上下文间松耦合协作','∴','inference','live']]}
  ]},
  flow:{title:'数据与消息架构',subtitle:'聚焦 MQTT 风险事件到护理闭环的可靠数据链路。',layers:[
    {label:'设备与接入 · EDGE',cols:4,nodes:[['smart-bed','智能护理床','床垫 · 离床 · 体征设备','▱','fact','external'],['wearable','穿戴与按钮','手环 · 紧急按钮','⌁','fact','external'],['mqtt','MQTT Broker','TLS 1.3 · mTLS · ACL','MQ','fact','live'],['mqtt-ingress','MQTT 接入器','QoS 1 · Schema · 幂等键','↓','inference','live']]},
    {label:'业务事件链 · EVENT PIPELINE',cols:5,nodes:[['telemetry','设备遥测','标准化属性与事件','01','inference','live'],['risk-event','风险事件','规则识别 · 去重','02','inference','live'],['alarm','业务告警','唯一告警 · 状态机','03','inference','live'],['care','护理任务','确认 · 处置 · 结果','04','inference','live'],['notification','通知与监管','授权摘要 · 交换任务','05','inference','external']]},
    {label:'可靠消息 · MESSAGING',cols:4,nodes:[['inbox','Inbox','消费幂等 · 重放保护','IN','inference','live'],['outbox','Outbox','业务事务内持久化','OUT','inference','live'],['rabbit','RabbitMQ','发布确认 · 持久队列','RM','fact','live'],['dlq','DLQ / 回灌','拒绝隔离 · 受控恢复','DL','inference','live']]},
    {label:'数据服务 · DATA',cols:4,nodes:[['mysql','MySQL / H2','事务数据 · Schema V10','DB','fact','live'],['audit','追加式审计','主体 · 动作 · 资源 · 结果','AU','inference','live'],['metrics','Prometheus Metrics','延迟 · 吞吐 · 错误','∿','inference','live'],['backup','备份与恢复','RPO 0 · RTO 4s 演练','↻','inference','live']]}
  ]},
  deployment:{title:'部署与安全架构',subtitle:'展示本地双实例、生产依赖和纵深安全控制。',layers:[
    {label:'访问边界 · ACCESS',cols:4,nodes:[['staff','机构运营终端','浏览器 · HTTPS','▣','inference','live'],['device-client','设备客户端','mTLS · Topic ACL','⌁','inference','live'],['external-channel','通知与政务通道','签名 · 超时 · 重试','↗','inference','external'],['idp','企业身份提供商','OIDC · JWKS','ID','inference','external']]},
    {label:'应用运行 · RUNTIME',cols:3,nodes:[['h2-app','H2 本地实例','8080 / 9090 · PID 12515','H2','inference','live'],['mysql-app','MySQL 集成实例','8180 / 9190 · PID 12528','MY','inference','live'],['gateway','HTTP 安全边界','API Key / OIDC · RBAC','GW','inference','live']]},
    {label:'基础设施 · INFRASTRUCTURE',cols:4,nodes:[['mysql','MySQL 8.4','Flyway V1–V10','DB','fact','live'],['rabbit','RabbitMQ 3.13','AMQP · DLX · DLQ','RM','fact','live'],['mqtt','Mosquitto 2.1.2','TLS 1.3 · mTLS','MQ','fact','live'],['sandbox','外部服务沙箱','通知 · 政务回执','SB','inference','live']]},
    {label:'安全与运行 · SECURITY & OPS',cols:4,nodes:[['tenant','租户隔离','请求上下文 + SQL 约束','⊞','inference','live'],['secrets','凭据治理','无密钥发布 · 轮换基线','✦','inference','live'],['observability','可观测性','Actuator · Prometheus','∿','inference','live'],['recovery','韧性与恢复','备份 · 重启 · 重连验证','↻','inference','live']]}
  ]}
};

window.NODE_DETAILS={
  'service-network':{k:'来源事实 · F-01',title:'智慧养老服务网络',desc:'连接养老机构、社区、家庭与政府监管部门，形成覆盖“居住—照护—健康—安全”的协同服务网络。',bullets:['机构、社区与居家场景统一协作','业务边界覆盖照护、设备与监管','产品能力以老人安全和服务闭环为核心'],interfaces:['机构门户','家属小程序','监管接口'],owner:'企业架构',source:'《招聘信息汇总.md》F-01'},
  mqtt:{k:'来源事实 · F-04',title:'MQTT 设备接入',desc:'以安全、可追踪的 MQTT 通道承接护理床、穿戴设备和紧急按钮事件。',bullets:['TLS 1.3 双向证书认证','QoS 1 消息与 Topic ACL','接入后先做模式与幂等校验'],interfaces:['MQTT 5','mTLS','Inbox'],owner:'IoT 平台',source:'《招聘信息汇总.md》F-04'},
  alarm:{k:'架构推演 · DDD',title:'告警中心',desc:'把设备风险信号转化为可分级、可确认、可处置、可升级的业务告警。',bullets:['业务告警唯一性与状态机约束','告警状态事件驱动照护和通知','处置过程全量记录并可审计'],interfaces:['RiskEvent','CareTask','Outbox'],owner:'告警域',source:'ARCHITECTURE.md · 架构推演'},
  rabbit:{k:'来源事实 + 工程实现',title:'RabbitMQ 消息中台',desc:'承接跨上下文业务事件，提供持久化发布、失败隔离和受控回灌。',bullets:['发布确认与持久化主队列','Dead Letter Exchange / DLQ','重启持久化与自动恢复已验证'],interfaces:['AMQP','Outbox','DLQ'],owner:'平台工程',source:'F-05 · M8–M10'},
  'h2-app':{k:'当前工程状态',title:'H2 本地运行实例',desc:'面向本地开发和演示的文件数据库实例，与 MySQL 集成实例并行运行。',bullets:['应用端口 8080，管理端口 9090','数据库模式已迁移至 V10','当前记录 PID 12515'],interfaces:['HTTP 8080','Actuator 9090','H2 File'],owner:'运行平台',source:'ROADMAP.md · 2026-08-17'},
  'mysql-app':{k:'当前工程状态',title:'MySQL 集成实例',desc:'连接真实 MySQL、RabbitMQ 和 Mosquitto 的生产拓扑验证实例。',bullets:['应用端口 8180，管理端口 9190','真实 MQTT → 告警 → AMQP 链路','租户隔离与 RBAC 负向测试通过'],interfaces:['HTTP 8180','Actuator 9190','MySQL 8.4'],owner:'运行平台',source:'ROADMAP.md · M8–M13'},
  family:{k:'来源事实 · F-02',title:'老人及家属协同',desc:'通过微信小程序让已授权家属获取最小必要的安全和服务信息。',bullets:['授权关系决定可见范围','告警只展示最小必要摘要','真实微信通道仍待外部验收'],interfaces:['微信小程序','授权校验','通知订阅'],owner:'家庭服务',source:'《招聘信息汇总.md》F-02'},
  'gov-adapter':{k:'来源事实 + 架构推演',title:'政务数据交换',desc:'通过隔离适配层将内部模型映射为版本化监管契约，避免政务字段侵入核心领域。',bullets:['读取经审批的数据视图','交换任务、重试和回执留痕','正式环境依赖监管契约和凭据'],interfaces:['REST','ExchangeTask','Receipt'],owner:'政务适配',source:'F-03 · Government Context'},
  identity:{k:'架构推演 · 安全基线',title:'身份与授权',desc:'统一管理人员、服务与设备身份，并将认证与业务授权明确分离。',bullets:['支持 API Key 与 OIDC 双模式','五类角色的 RBAC 权限矩阵','租户范围在应用与 SQL 两层约束'],interfaces:['OIDC','API Key','RBAC'],owner:'身份域',source:'ADR / M11'},
  backup:{k:'当前工程状态',title:'备份与恢复',desc:'对 MySQL 数据执行可验证的备份恢复演练，并记录恢复点与恢复时间。',bullets:['临时库恢复验证数据完整性','实测 RPO 0','实测 RTO 4 秒'],interfaces:['MySQL Dump','Restore Drill','Audit'],owner:'SRE',source:'ROADMAP.md · M13'}
};

window.DEFAULT_DETAIL={k:'架构推演',desc:'该节点是 SmartCareOS 企业级智慧养老架构中的职责单元。点击其他节点可继续查看其边界与工程状态。',bullets:['职责在所属限界上下文内闭合','通过稳定接口与事件协作','敏感操作纳入租户与审计控制'],interfaces:['REST API','Domain Event','Audit'],owner:'SmartCareOS',source:'ARCHITECTURE.md'};
