# Context Map

## Contexts

- [Identity](./contexts/identity/CONTEXT.md) — 管理人员、服务、设备身份及业务授权
- [Institution](./contexts/institution/CONTEXT.md) — 管理机构组织、空间、床位和人员责任范围
- [Elder](./contexts/elder/CONTEXT.md) — 管理老人档案、入住、联系人和授权关系
- [Device](./contexts/device/CONTEXT.md) — 管理设备身份、产品模型、绑定、影子和命令
- [Alarm](./contexts/alarm/CONTEXT.md) — 管理风险告警的状态、级别、升级和处置引用
- [Care](./contexts/care/CONTEXT.md) — 管理护理计划、任务、记录与交班
- [Notification](./contexts/notification/CONTEXT.md) — 管理订阅、通知内容和投递结果
- [Government](./contexts/government/CONTEXT.md) — 隔离并适配政务数据交换契约

## Relationships

- **Institution → Elder**: Institution 提供机构、床位和人员范围；Elder 管理入住关系。
- **Elder → Device**: Device 仅引用老人和床位 ID，并维护有时间范围的绑定。
- **Device → Alarm**: Device 发布归一化风险来源事件；Alarm 负责业务告警。
- **Alarm → Care**: Alarm 请求护理任务；Care 返回任务处置结果。
- **Alarm → Notification**: Alarm 发布状态事件；Notification 在投递时重新校验授权。
- **Elder/Institution → Government**: Government 通过版本化映射读取已批准数据，不拥有业务主数据。

