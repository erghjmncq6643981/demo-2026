# FCC IVR Flow Studio 设计

本页只描述已实现的管理面契约，不把发布通知或 FNode 同步应答当作真实通话完成。

## 业务模型

`fcc-common/src/main/resources/flows/system-models.json` 是呼入、坐席先接外呼、坐席终端主动外呼、通知外呼和话机绑定五个固定模型的唯一来源。管理页通过后端模型和动作目录显示 action 中文名、执行器类型与 operation，不在 Vue 中复制 Java 动作枚举。

可编辑定义只支持 `routeMode=IVR`、`template=INBOUND` 的固定阶段：

```text
ENTRY -> MENU -> BRANCH -> ROUTE -> BRIDGE
      -> RECORD_START -> CONNECTED -> RECORD_STOP
      -> RATING -> RATING_SAVE -> CLOSING -> END
```

动作和顺序不可修改。画布只允许配置菜单媒体、收号超时、单键 if/else 分支、坐席/技能组目标、排队时限和未接通处理；录音、服务评价和结束语音是系统固定阶段。后端拒绝重复按键、非安全媒体路径、未知字段、旧 `DID_DIRECT` 模型及任意脚本/类名/URL。

`call-center-backend` 用于核对多年运行后形成的业务闭环，包括入口分流、路由失败出口、同组代答、漏话、评价、转接、自动外呼确认和业务系统回调；它的多 topic、硬编码流程和隐藏条件分支不作为画布结构。只有已经进入公共动作目录、具备管理端校验、运行端执行和动作事实记录的能力，才允许出现在 Flow Studio 中。当前未动作化的营业时间、溢出、复杂转接和具体第三方回调继续显示为待完善能力，不提供看似可配置但运行端无法兑现的控件。

## 呼入入口与被叫号码

被叫号码是入口主数据，不属于版本 JSON。通信资源表 `fcc_did_number` 保存运营商/XSwitch 实际送达的 DID，`route_key` 指向稳定 `flow_key`。同一流程可绑定多个 DID；一个 DID 同时只归属一个流程，改绑在后端一次更新完成。Sidecar 的规范 Channel 事件携带 `dest_number`，fcc-server 按号码找到流程并固定当时最新的已发布版本。

Flow Studio 在版本工作区上方显示本流程的 DID，可绑定、改绑或解绑已录入号码。没有启用 DID 的呼入流程不允许发布，避免产生永远无法接入的线上版本。真实运营商送号格式仍需在 FreeSWITCH 联调环境核对。

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

版本序号完全由后端分配。无版本流程显示“创建首个草稿版本”；草稿可编辑和保存；已发布/历史版本只读，需要显式“基于此版本新建草稿”。每个流程最多一个草稿，已有草稿时从历史版本返回现有草稿，不覆盖它。`BRANCH` 面板同时维护各个 `if` 按键和唯一的 `else/defaultRoute`；`ROUTE` 面板引用同一对象。

## 通话执行轨迹

通话详情中的“通话过程”是模型的精简业务流，不是第二个编辑器。它从数据库读取通话启动时固定的模型快照和每次阶段尝试，显示 action、状态、耗时、`commandId`、`eventId`、输入/输出与错误。无持久记录时明确显示“没有流程记录”，不模拟进度。

## 未验证边界

- 发布后 Redis 与 HTTP reload 是 best-effort，尚无持久 Outbox、重试和多实例激活看板。
- FNode 同步 `ACCEPTED` 只表示 Sidecar 受理；通话完成以持久事件和阶段事实为准。
- 真实 FreeSWITCH、NATS/JetStream、媒体、数据库、认证浏览器和 Windows 弹屏需要在部署环境验收。
