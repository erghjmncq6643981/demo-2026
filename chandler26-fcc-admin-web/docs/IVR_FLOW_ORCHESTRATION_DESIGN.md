# FCC IVR Flow Studio 现状

更新：2026-09-20。本页只描述已实现的管理面契约，不把发布通知或 FNode 同步应答当作真实通话完成。

## 业务模型

`fcc-common/src/main/resources/flows/system-models.json` 是呼入、坐席先接外呼、通知外呼和话机绑定四个固定模型的唯一来源。管理页通过后端模型和动作目录显示 action 中文名、执行器类型与 operation，不在 Vue 中复制 Java 动作枚举。

可编辑定义只支持 `routeMode=IVR`、`template=INBOUND` 的固定阶段：

```text
ENTRY -> MENU -> BRANCH -> ROUTE -> BRIDGE -> CONNECTED -> END
```

动作和顺序不可修改。画布只允许配置菜单媒体、收号超时、单键 if/else 分支、坐席/技能组目标、排队时限和未接通处理。后端拒绝重复按键、非安全媒体路径、未知字段、旧 `DID_DIRECT` 模型及任意脚本/类名/URL。

## API 与状态

统一边界是 `/api/admin/flow-studio`：

| 接口 | 用途 |
| --- | --- |
| `GET /actions` | 公共 action 与三类执行器目录 |
| `GET /models`、`GET /models/{template}` | 固定系统模型 |
| `GET /flows?pageNum=&pageSize=` | 分页流程摘要，不返回版本 JSON |
| `POST /flows` | 创建流程主数据 |
| `GET /flows/{flowKey}` | 单流程摘要 |
| `GET /flows/{flowKey}/versions?pageNum=&pageSize=` | 分页版本摘要 |
| `GET /flows/{flowKey}/versions/{versionNo}` | 按需读取完整 `definitionJson` |
| `PUT /flows/{flowKey}/draft` | 严格校验并保存唯一草稿 |
| `POST /flows/{flowKey}/publish` | 发布草稿，返回运行端 `PENDING` |
| `GET /calls/{callId}?after=` | 游标分页读取真实执行轨迹 |

`useFlowEditor.ts` 拥有分页、流程/版本选择、详情按需加载、脏数据确认、草稿和发布状态。`IvrFlowView.vue` 只组合画布、参数编辑器和版本控件。保存与发布失败均保留用户当前编辑内容。

## 通话执行轨迹

通话详情中的“通话过程”是模型的精简业务流，不是第二个编辑器。它从数据库读取通话启动时固定的模型快照和每次阶段尝试，显示 action、状态、耗时、`commandId`、`eventId`、输入/输出与错误。无持久记录时明确显示“没有流程记录”，不模拟进度。

## 未验证边界

- 发布后 Redis 与 HTTP reload 是 best-effort，尚无持久 Outbox、重试和多实例激活看板。
- FNode 同步 `ACCEPTED` 只表示 Sidecar 受理；通话完成以持久事件和阶段事实为准。
- 真实 FreeSWITCH、NATS/JetStream、媒体、数据库、认证浏览器和 Windows 弹屏需要在部署环境验收。
