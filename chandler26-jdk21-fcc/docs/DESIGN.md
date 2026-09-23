# FCC Java 控制面设计

## 1. 文档状态

本文描述 `chandler26-jdk21-fcc` 的当前代码边界。本项目是全新 FreeSWITCH 呼叫中心，不继承参考项目的旧协议、旧流程定义、演示账户或业务兼容层，也不引入租户模型。未完成或未验证的能力明确列为已知缺口。

跨工程入口见 [FCC 文档导航](../../docs/README.md) 和 [产品设计](../../docs/fcc-product-design.md)。优先级与交付状态只在 [交付计划](../../docs/fcc-delivery-plan.md) 中维护。

技术基线：

| 项目 | 当前配置 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.1 |
| ORM | MyBatis-Plus 3.5.17 |
| 数据库 | MySQL 8+ |
| 运行态缓存 | Redis 7+ |
| 软交换协议 | NATS Core 命令 + JetStream 事件 + JSON-RPC 2.0 + Go Sidecar |
| 管理鉴权 | Sa-Token |
| 服务端口 | `fcc-server :8085`、`fcc-admin :8089` |

## 2. 系统边界

```text
fcc-admin-web --> fcc-admin :8089 ------> MySQL / Redis
                         |
                         +--------------> Sidecar :8088 (分机/资源管理)

fcc-client-web --> fcc-admin :8089
               --> fcc-server :8085 ----> MySQL / Redis
                         |        |
                         |        +------> /ws/agent
                         v
                        NATS <----------> Sidecar --> ESL --> FreeSWITCH
```

Java 控制面不连接 ESL。FreeSWITCH 命令统一通过 `FNode.*`，节点运维通过显式 Sidecar HTTP Client。

### 2.1 管理面与运行面职责

管理面遵循“在 admin 维护/查看，在 server 使用/执行”。这句话描述的是用例入口，不表示把运行事实复制到 admin：fcc-admin 提供统一管理 API、权限校验和审计入口；fcc-server 持有客户、自动外呼和通话运行事实并执行调度与话务动作。两者通过窄 HTTP/消息边界协作，不新增实现模块依赖，也不建立两份可相互覆盖的业务事实。

| 能力 | fcc-admin / admin-web | fcc-server |
| --- | --- | --- |
| IVR Flow Model | 创建草稿、维护参数和 if/else、绑定 DID、校验、发布、查看版本 | 加载已发布不可变版本，为新通话固定版本，执行 action 并记录阶段事实 |
| 客户资料 | 管理员维护与查看入口、`business:manage` 权限校验 | 保存客户事实、号码规范化、坐席归属、来电匹配和弹屏摘要 |
| 自动外呼 | 创建、暂停、恢复、取消、任务及逐次结果查看入口 | 持久任务、领取租约、时段/频控、发起通话、结果对账和重试 |
| 通话过程 | 查询 Call、Leg、Bridge、录音和简化执行轨迹 | 写入通话与流程执行事实，处理事件和终态幂等 |

浏览器只访问 `/api/admin/business/**`；fcc-admin 先校验 `business:manage`，再向 `/internal/business/**` 转发当前令牌。fcc-server 不信任转发方声明的角色，而是在线调用 fcc-admin `/api/admin/auth/me`，要求 `accountType=CONSOLE` 且权限含 `business:manage` 或 `*`。`/auth/me` 每次从数据库重新核验控制台账号状态和角色，因此停用、删除或改权不继续沿用登录时的旧权限。内部接口还需要由部署网络边界限制，不能暴露为面向浏览器的公共 API。

fcc-server 统一拥有话机绑定运行流程、呼入、呼出、自动外呼调度、客户资料和 Windows 弹屏业务。自动外呼和客户资料不下沉 Sidecar，也不由坐席客户端维护业务事实。

弹屏链路为 FreeSWITCH → Sidecar → fcc-server 关联 Call、坐席与客户 → 业务 WebSocket → Windows 客户端执行本机通知。收件人、内容授权、关闭时机、补发规则由 fcc-server 决定；Windows API 由本机客户端调用，服务端不能直接恢复远程桌面窗口。

采用固定的绑定、呼入、呼出、自动外呼模板，保持模块化单体。话机通过拨打 `0000` 进入绑定流程，Sidecar 上报已认证 SIP 分机，坐席输入工号后由 fcc-server 原子换绑。客户资料、自动外呼调度和 Windows 弹屏业务归 fcc-server；调度任务先持久化，写 Call 前在同一事务锁定派发租约。过期且无 Call 的尝试原子失败，已存在 Call 的未知结果不重拨。实施与证据见 [跨电脑部署验收](../../docs/fcc-cross-machine-acceptance.md)。

## 3. Maven 模块

### 3.1 fcc-common

包含跨模块稳定契约：

- FNode 命令 DTO 和 JSON-RPC 报文；
- Event DTO；
- Call/Flow 公共实体；
- 状态、动作、方向等枚举；
- ID、时间和公共返回结构。

该模块不依赖其他项目模块，也不承载 Controller、Mapper 或运行时服务。

### 3.2 fcc-server

当前包含：

- `command`：NATS `FNode.*` 请求/应答；
- `event`：订阅 `fs.event.>` 并驱动 Channel、DTMF、注册和录音事件；
- `call`：内存会话索引；
- `flow`：阶段处理、动作执行和流程配置；
- `management`：承接 fcc-admin 的受控客户与自动外呼管理命令/查询，不向坐席客户端开放维护入口；
- `customer`、`outbound`：客户事实、自动外呼任务、调度、逐次尝试和结果对账；
- `infrastructure.persistence`：Call、Leg、Bridge、Event、Command 和 Recording 持久化；
- `recording`：共享录音路径；
- `websocket`：话务 REST、Agent WebSocket 和来电弹屏。

### 3.3 fcc-admin

当前包含：

- 登录、当前用户和密码操作；
- 管理员与坐席账户；全新空库可通过显式环境变量执行一次性首个管理员初始化，已有账号时不覆盖口令；
- 客服组、组员、终端绑定和代班；
- 分机配置；话机拨号输入工号的运行绑定由 fcc-server 负责；
- CDR、统计、详情和录音读取；
- 回拨任务；
- 流程动作目录、固定模型、分页摘要、版本详情、草稿和发布；
- 客户资料与自动外呼的维护/查看 API；实际事实、调度和执行仍在 fcc-server；
- DID、外呼号码使用的拨号上下文，以及节点运行状态；
- 系统配置和客户端版本/硬件记录。

### 3.4 starter SDK

`fcc-server-starter` 和 `fcc-admin-starter` 是供其他服务依赖的远程调用 SDK 边界，不是服务启动模块，不得依赖对应服务的实现模块。当前没有跨服务 Feign 调用需求，因此两个模块不引入 OpenFeign，也不预置无调用方的接口或 DTO。服务启动类、运行配置和集成测试分别由 `fcc-server`、`fcc-admin` 自己承载。

## 4. 身份语义

| 字段 | 含义 |
| --- | --- |
| `call_id` | FCC 业务通话聚合 ID；无前缀正整数雪花 ID，数据库 BIGINT、API 字符串 |
| `channel_uuid` | 单个 FreeSWITCH Channel/Leg；独立生成的标准 36 位 UUID |
| `ctrl_id` | 命令与事件关联 ID |
| `node_id` | Sidecar/FreeSWITCH 节点 |
| `bridge_uuid` | 一次媒体桥接 |
| `command_id` | 一次 FCC 命令 |
| `event_id` | 一次标准事件 |
| `flow_instance_id` | 一次流程执行 |
| `biz_id` | 外部业务关联标识 |

标识决策：参考项目 call-center-backend 将业务 callUuid（标准 UUID）和 Long 数据库主键分开。新项目保留“业务通话与话道分别标识”的原则，业务 callId 直接复用聚合主键，不再额外引入 callUuid。所有关联表使用同一数值身份；禁止添加 `call-` 前缀、截前缀兼容或哈希兜底。非法 ID 明确拒绝，不生成替代 ID。

这些值不得互换。共享 `FccIdentifierJacksonModule` 已在 admin/server 注册：Bean 中名为 `id` 或以 `Id` 结尾的 Long/long 属性按字符串输出，时长与统计值保持数值。该规则不等于所有 Map 值、集合元素、其他命名或独立 ObjectMapper 都已覆盖；JDK 21 编译及标识序列化单元测试已通过，实际 Spring HTTP 输出仍需完整验收。前端继续按不透明字符串处理身份。

## 5. 呼叫控制链路

1. 坐席端调用 `/api/telephony/call/*`。
2. Controller 建立或查找 `CallInfoBO` 和内存会话索引。
3. Flow/Action 生成 FNode DTO。
4. `FccClient` 请求逻辑主题 `fs.cmd.dispatch` 并等待 Sidecar 同步应答；业务服务不提供节点参数。
5. Sidecar 调用 FreeSWITCH。
6. Sidecar 发布 `Event.Channel`、`Event.DTMF`、`Event.Recording` 或 `Event.Registration`。
7. `FccEventListener` 更新会话、持久化事实、触发后续动作并推送 Agent WebSocket。
8. 坐席端以业务 WebSocket和 SIP Session 对账最终状态。

同步 RPC 成功不是振铃、接通、桥接或录音成功的最终证据。

### 5.1 命令路由边界

呼叫中心业务只表达 `Dial`、`Play`、`ReadDTMF`、`Bridge`、`Record`、`Hangup`、`Transfer` 等逻辑命令。`FccClient` 统一发布到 `fs.cmd.dispatch`，命令参数只携带控制标识、话道 UUID、目标号码、`routing_context` 和媒体等业务信息，不携带 `node_id`，也不拼接 `sofia/gateway/...` 等 FreeSWITCH 拨号串。

`routing_context` 是呼叫中心配置选择的业务路由入口，例如内部分机 `default`、移动或电信的独立 context；具体 gateway、SIP profile 和运营商线路由 FreeSWITCH/Sidecar 配置拥有。Sidecar 在受控校验后把“目标号码 + context”转换为节点侧拨号表达式。`fcc_telephony_trunk`、`trunk_id`、`trunk_code` 和 `gateway_name` 不再是 FCC 运行时路由模型。

Sidecar 的 dispatch ingress 在单节点部署中可直接调用本地 Dispatcher；多节点部署由独立 Coordinator 根据新建 Dial 的健康、容量、context 可用性，以及既有 `channel_uuid` 的 ownership 选择节点，再转发到内部 `fs.cmd.{nodeId}`。所有 Sidecar 仍以 `fs.event.{nodeId}.{category}` 发布事件，事件中的 `node_id`、CallLeg 的 `node_id` 和命令应答的 `node_id` 是基础设施事实，用于归属、审计、去重和恢复，不是业务路由参数。

`FNode.ChannelSnapshot` 通过逻辑入口返回 Coordinator 聚合的完整话道集合。当前代码已实现逻辑入口和单节点直执行；跨节点 ownership registry、跨节点 Bridge 和快照聚合需要多 Sidecar 环境联调，不能以单节点测试宣称完成。

### 5.2 人工外呼与坐席可用性

终端绑定、终端注册事实和坐席业务状态是三个独立概念。系统发起 Dial 前只要求存在启用的 SIP/WebRTC 绑定，并通过 `fcc_agent_presence` 的 `READY/BUSY/REST/ACW` 做业务并发控制；`login_status`、最近注册事件和 WebRTC/SIP 在线投影不再作为拨号同步硬前置，避免把注册状态同步延迟误判为坐席不可用。终端是否真实可达由 Sidecar 命令应答和后续 Channel 事件判定，注册事件继续用于管理端展示、诊断和告警。

人工外呼有两个固定模型：

1. `AGENT_FIRST`：REST/API/自动任务发起，先 Dial 坐席绑定终端；坐席 Leg `READY` 后 Dial 客户，客户 Leg `READY` 后 Bridge。
2. `AGENT_ORIGINATED`：坐席在已认证 SIP/WebRTC 终端通过内部 `default` context 拨客户号码；Sidecar 上报 `authenticated_extension`、真实被叫和 context，fcc-server 接管现有坐席 Leg、原子占用坐席，再 Dial 客户并 Bridge。未绑定坐席的认证终端会被拒绝，不能回落为普通客户呼入。

两条路径共享 Call/Leg/Bridge 事实和终态幂等处理，但入口与首个动作不同，不能把终端主动拨号伪装成“系统先拨坐席”。`AGENT_ORIGINATED` 已有固定模型和事件入口；真实 FreeSWITCH 默认 context 的外呼捕获规则、号码前缀处理及异常事件顺序仍需联调验证。

## 6. 当前 API

### 6.1 fcc-server

基础路径 `/api/telephony/call`：

- `outbound`
- `hangup`
- `hold`
- `dtmf`
- `supervise`
- `transfer`
- `flow/reload`

WebSocket：`/ws/agent`，通过认证子协议头核验管理端登录身份，不接受查询工号作为身份依据。

坐席本人运行接口：

- `GET/POST /api/telephony/agent-state`：读取或更新本人坐席状态，通话占用由服务端维护；
- `GET/POST /api/telephony/calls/{callId}/summary`：读取或提交本人已结束通话的小结，提交幂等且不能释放较新通话的占用；
- `GET /api/telephony/callbacks`、`POST /api/telephony/callbacks/{id}/call`：查询本人或未分配回拨，原子领取并创建调度任务；
- `/ws/agent` 的 `SCREEN_POP_RECEIPT`：按认证工号和通话保存展示事实，不把展示回执视为接听。

### 6.2 fcc-admin

基础路径 `/api/admin`：

- `auth`
- `users`
- `agents`
- `extensions`
- `cdrs`
- `recordings`
- `callbacks`
- `flow-studio`
- `resources`
- `configs`
- `fleet`

增长型集合必须分页；Leg、Trace、流程 JSON 和录音元数据通过详情接口加载。Flow Studio 的流程列表和版本列表均只返回分页摘要，选中具体版本时再加载 `definitionJson`；单通话执行轨迹按游标分页。

## 7. 数据持久化

### 7.1 当前 Mapper 覆盖

`fcc-server` 当前有 Mapper：

- `fcc_call_session`
- `fcc_call_leg`
- `fcc_call_bridge`
- `fcc_call_bridge_member`
- `fcc_call_event`
- `fcc_call_command`
- `fcc_call_recording`

`fcc-admin` 当前有 Mapper 覆盖坐席、组、终端绑定、分机、话单读取、资源、系统配置和客户端管理。

### 7.2 Baseline DDL

`docs/fcc-schema.sql` 定义的表范围大于当前业务实现。Flow Instance、Step Execution、Route Attempt、Agent Service Session、Dial Job/Attempt、Outbox 等表属于数据基线，但不能仅凭 DDL 声称对应运行链路已经完整落地。

MySQL 是持久事实来源。Redis 只存可重建的运行态和通知。内存 `CallSessionManager` 是当前事件关联索引，不是持久事实来源。

通信资源基线只保存 FCC 需要的业务选择：`fcc_did_number(routing_context, phone_number)` 定位呼入入口，`fcc_outbound_number(routing_context, phone_number)` 提供出局 context 与主叫号码，`fcc_call_leg.routing_context` 保存实际话道快照。当前是开发期全新项目，不保留 trunk/gateway 表的数据升级路径；表结构变更后直接使用 `docs/fcc-schema.sql` 重建空库。

## 8. 流程版本

管理端支持流程：

- 分页流程摘要和分页版本摘要；
- 单版本完整定义按需查询；
- 公共动作目录和五个固定系统模型查询；
- 草稿保存；
- 发布；
- 按通话查询实际经过的阶段、action、指令、事件和结果；
- 发布后 Redis 通知；
- 对 `fcc-server` 发送 HTTP reload 通知。

固定模型的唯一来源是 `fcc-common/src/main/resources/flows/system-models.json`：

| 模型 | 运行入口 |
| --- | --- |
| `INBOUND` | DID 呼入、菜单、分支、路由、桥接、录音、评价和收尾 |
| `AGENT_FIRST` | 系统先呼坐席，再呼客户 |
| `AGENT_ORIGINATED` | 接管已认证坐席终端主动发起的 Leg |
| `NOTIFICATION` | 通知外呼、放音、收号和确认 |
| `PHONE_BINDING` | 已认证物理话机拨 `0000` 后绑定工号 |

五类模型均使用 `executionMode=FIXED_RUNTIME`。真实号码、坐席、技能组、context 和媒体目录属于环境或业务配置，不进入系统模型初始化数据。基线 SQL 中的模型数据由以下命令生成，修改资源后必须重新生成并审查差异：

```bash
node tools/generate-system-models.mjs
```

呼入入口和流程版本是两个不同层次。`fcc_did_number.phone_number` 保存 XSwitch/运营商实际送达的被叫 DID，`route_key` 绑定稳定 `flow_key`；同一流程可绑定多个 DID，一个 DID 同时只指向一个流程。Sidecar 将真实 `dest_number` 放入规范 Channel 事件，fcc-server 先按 DID 找到绑定，再固定当时最新的已发布版本。号码不写入版本 JSON，号码调整也不会篡改历史流程版本。Flow Studio 可绑定和解绑已录入 DID；没有启用 DID 的呼入流程不能发布。

发布接口成功表示数据库版本切换完成，运行端状态返回 `PENDING`；这不等于所有运行实例或 FreeSWITCH 节点已验证切换。发布事务先锁定流程主定义行，再把原 `PUBLISHED` 版本改为 `ARCHIVED`，最后把草稿改为 `PUBLISHED` 并更新 `current_version`。`fcc_flow_definition_version.published_marker` 对 `PUBLISHED` 状态建立唯一索引，因此同一流程编码在数据库层最多只能有一个生效版本；并发发布失败时事务回滚，旧版本仍保持生效。新通话在创建 Flow Instance 时固定版本快照，存量通话不随发布改写。

可编辑流程只支持 `routeMode=IVR` 的呼入固定阶段：`ENTRY -> MENU -> BRANCH -> ROUTE -> BRIDGE -> RECORD_START -> CONNECTED -> RECORD_STOP -> RATING -> RATING_SAVE -> CLOSING -> END`。编辑者只能修改菜单媒体、收号超时、单键 if/else 分支、坐席/技能组目标、排队时限和未接通处理；录音、评价和结束语音使用系统固定动作，不允许画布把它们替换成任意脚本。不接受旧 `DID_DIRECT`、任意 Java 类/方法、脚本、表达式或动态 URL。

版本号由服务端分配，前端不允许手填。无版本流程通过“创建首个草稿版本”进入工作区；已发布和历史版本只读，需要基于已发布版本创建新草稿后才能修改。保存草稿不会影响当前通话；只有发布才切换生效版本。`BRANCH` 中的 `else` 直接维护唯一的 `defaultRoute`，`ROUTE` 阶段展示和编辑同一份兜底路由，不存在两套相互冲突的数据。

运行端采用按需加载：首次需要某个流程时查询其 `PUBLISHED` 版本并完成动作目录校验，使用 Caffeine `expireAfterWrite=3h`；写入后 175 分钟（剩余 5 分钟）触发异步刷新。刷新失败保留旧快照，过期且无法重新加载时才拒绝新的流程实例。Redis 发布通知只负责主动刷新/失效对应键，不会改写已经固定版本的通话实例。启动阶段不再全量读取所有流程，减少服务启动对数据库和流程数量的耦合。

### 8.1 业务闭环参考和模型演进

`call-center-backend` 运行多年，其 XSwitch 多 subject 入口、硬编码 Flow 和历史表结构不是新系统架构模板，但其业务细节可作为验收清单：号码入口分流、IVR/直达、排队与顺振、无人/超时出口、录音、评价、漏话回拨、盲转/咨询转/三方、自动外呼确认，以及司机热线等第三方业务回调。`chandler26-jdk17-freeswitch-FCC` 的 FNode 链路仅作为协议回归参考。

参考旧系统时以“业务输入、判断、动作、持久事实、外部通知、失败出口是否闭环”为抽取单位，不以 Controller、Listener、NATS subject、数据库表或隐藏代码分支为复制单位。旧系统中按运营商拆分 subject 的真实业务含义是保留入口号码、线路/运营商归属和路由上下文；新系统由统一标准事件中的 `node_id`、`dest_number`、`context`、DID/外呼号码配置及固定流程版本承载这些信息。gateway 和 SIP profile 留在 FreeSWITCH/Sidecar 运维边界。

| 旧系统已打磨的业务语义 | 新系统承载方式 | 当前证据与缺口 |
| --- | --- | --- |
| 不同运营商/400 入口、被叫号码与直达入口 | 标准 Channel 事件的 `context`/`dest_number` + DID 主数据 + `flow_key` | DID 按 context 与号码精确绑定已实现；真实运营商送号格式、前缀归一化和 context 映射待联调 |
| IVR 收号、按键分支及无输入兜底 | `MENU`、`BRANCH`、唯一 `defaultRoute` 和 `timeoutAction` | 编辑、校验和运行分支已实现；真实音频、DTMF 超时事件待 FreeSWITCH 验证 |
| 指定坐席、技能组、同组代答、忙/离线/无人和多次尝试 | 路由尝试事实 + 并发预占 + 可替换路由策略 | 指定坐席/技能组与排队期轮询已实现；代班、营业时间、溢出层级及细分失败原因尚未动作化 |
| 来电弹屏、接听、桥接、挂机和坐席释放 | Call/Leg/Bridge 事实 + Windows 客户端回执 + 终态幂等 | 核心链路和弹屏回执已实现；真实 SIP/Windows 联调与异常恢复验收未完成 |
| 未接、超时、客户先挂产生漏话及回拨闭环 | `FINALIZE_INBOUND` + `fcc_callback_task` + 渐进式外呼任务 | 未接回拨创建、领取和任务关联已实现；运营规则、SLA 和人工处置结果仍需补齐 |
| 录音、满意度评价及文件完成态 | `START_RECORDING`/`STOP_RECORDING`、`PLAY_NAVIGATION_VOICE`、`COLLECT_SERVICE_RATING`、`PLAY_CLOSING_VOICE`、`PERSIST_SERVICE_RATING` + Recording/评价事实 | 桥接后幂等开始录音，终态前停止录音；坐席先挂机时收取 1-5 分评价，评价或超时后播放预设结束语音再挂机。真实 FreeSWITCH 录音文件完成态仍待联调 |
| 盲转、咨询转、三方与转接后话单归属 | 显式转接动作 + 多 Leg/Bridge 成员事实 | 数据模型可承载，完整动作与生命周期尚未实现，不能以普通桥接代替 |
| 自动外呼放音、按键确认、重试和终态通知 | 通知外呼固定模型 + 持久调度/尝试 + 确认事实 | 调度、租约和确认模型已有基础；真实并发、重试、音频与终态通知待联调 |
| 司机热线路由、港口映射和结束后同步业务系统 | 命名第三方端点 + 显式请求/响应动作 + 幂等业务回调事实 | 通用 HTTPS 执行边界已存在；具体业务契约、端点配置、补偿与对账尚未实现 |

新系统当前已对象化并运行的呼入动作是 DID 解析、菜单收号、if/else 路由、坐席预占与呼叫、桥接、录音、坐席先挂机后的评价/结束语音和未接通回拨收尾；菜单与通知文案的 TTS 生成由 Sidecar 完成。复杂转接、营业时间/溢出、第三方业务回调等不能继续隐藏在监听器条件分支中；后续加入时必须先进入 `fcc-common` 动作目录，声明 FNode 指令、内部方法或第三方接口执行边界，再由 admin 校验、server 执行并记录每次动作事实。在这些动作真正接入运行流程并验证前，文档不将其描述为可配置完成。

`FlowActionType` 是 admin/server 共用动作目录，每个动作明确归属 FNode 指令、内部业务方法或第三方接口。每个 `FNodeMethod` 都必须至少有一个对应动作；一个底层方法允许有多个业务语义，例如坐席外呼和客户外呼都使用 `FNode.Dial`。FNode 方法、事件方法/字段及命令选项由 `FNodeMethod`、`FccEventMethod`、`FccEventField`、`FNodeMediaType`、`FNodeDtmfPostAction`、`FNodePlayPostAction`、`FNodeRecordAction` 等公共对象定义，不在执行器中解析裸 wire 字符串。转接只发送业务 `target` 和 `context`，FreeSWITCH 的 `XML` 表达式由 Sidecar 生成。

内部动作只保留高内聚的业务闭环，例如 DID 解析、路由分支选择、坐席预占与收尾；播放、录音、转接、挂机等 FreeSWITCH 能力必须建模为 FNode 动作，不得复制成内部动作。第三方动作使用固定 `ThirdPartyFlowRequest/ThirdPartyFlowResponse` 协议：请求由 FCC 补齐协议版本、`commandId`、`callId`、流程实例和动作编码，端点只能来自服务端已启用的 HTTPS 配置；响应必须原样回传协议版本和 `commandId`，并提供 `accepted`、业务码、消息和结构化 `data`。发布通知在 afterCommit 执行，但尚无持久通知重试与激活看板。

## 9. 录音

`fcc-server` 根据共享录音根目录生成并下发文件路径，Sidecar/FreeSWITCH 写文件，Java 保存录音元数据，`fcc-admin` 提供授权读取接口。

两个服务必须配置相同的 `FCC_RECORDING_BASE_DIR`。管理端只允许读取根目录范围内的文件；生产环境不得开放任意路径读取。

## 10. 当前可靠性边界

已经实现：

- FNode 同步请求超时；
- Call/Leg/Command/Event/Recording 持久化；
- 部分重复写入保护；
- WebSocket 心跳响应和多 Session 推送；
- 单节点 `fs.cmd.dispatch` 直执行；多节点 Coordinator 的 ownership、容量选择和跨节点 Bridge 仍未完成；
- 共享录音路径范围校验。

仍需作为已知缺口处理：

- Sidecar 事件现有稳定源 `event_id` 与落盘 outbox；Java Inbox 在业务分发前去重，`fcc_call_event` 先幂等写入 `RECEIVED`，处理器完成后回填 `PROCESSED/FAILED` 及可解析的 Call 关联。明确处理失败会 NAK 并最多重新领取 5 次，已完成的重复事件直接 ACK；遗留 `PROCESSING` 或耗尽重试的 `FAILED` 转为 `UNKNOWN`，等待人工或恢复任务对账；事件事实与 Inbox 仍不是同一数据库事务，需保留重放/对账指标。
- 录音事件已统一为 `Event.Recording`，分类为 `record`；Java 在桥接后登记共享路径、幂等发送 START/STOP，并将停止未知写为 `UNKNOWN` 等待对账；真实完成态、时长和文件大小仍需 Sidecar/FreeSWITCH 联调验证；
- NATS 事件使用 `FCC_EVENTS` JetStream；`fcc-control` durable 每次处理一条并显式 ACK。业务副作用与 Inbox 状态仍不是同一事务，真实事件写入、重投和故障恢复必须在部署环境验收；
- 当前只支持一个活跃 fcc-server，不能把共享 durable 等同安全的多实例会话处理；
- 副作用命令已按操作边界生成跨请求稳定的 `cmd-` 幂等标识，超时查询仍使用查询请求自身的传输标识；真实 Sidecar 重复命令和跨实例结果对账尚未联调验证；
- 消费前从 MySQL 分页恢复固定模板会话；已有 ChannelSnapshot 双方持续缺失对账，失败快照不视为挂机，单边残留和桥接重建仍需补齐；
- 流程发布数据库切换已由流程主行锁和 `published_marker` 唯一约束保证；Redis 与 HTTP 仍是提交后的 best-effort 运行端通知，通知失败不会回滚已提交版本，需通过重载接口或运维告警补偿；
- Agent WebSocket 已通过令牌在线核验坐席身份；身份服务故障时拒绝收发，尚需真实环境验证及性能评估；
- 共享 Jackson 标识符模块已加入，但命名外字段、Map 和实际 HTTP 输出仍需完整契约验证；
- Dial Job 已有持久领取、频控、时段、暂停/取消、逐次结果、超时未知保留和通话事实回填；真实话务与多实例恢复尚未完整验收。
- 坐席终端主动外呼已有模型、事件识别和 Leg 接管代码；真实默认 context 拨号计划、号码转换、重复/乱序/先挂机等场景尚未完成 FreeSWITCH 联调。

挂机/转接已校验本人 callId、话道 UUID 和底层响应，并分别进入 `HANGUP_CALL`/`TRANSFER_CALL` 公共动作；返回 ACCEPTED 而非最终状态，错误/超时不再伪造成功。保持和通话中 DTMF 目前仍通过受控 NativeAPI，班长四类干预均明确返回 501，前端禁用。保持媒体完成态和完整转接生命周期仍需联调。

## 11. 安全与运维

- 数据库、Redis、NATS、Sidecar、SIP 和录音配置从部署环境注入。
- 管理操作需要后端授权；隐藏按钮不能替代权限校验。
- 电话号码、录音、协议原文和文件路径属于敏感数据。
- 强拆、流程发布、分机、DID/context 变更和录音访问需要业务审计。
- `FNode.NativeAPI` 只允许受限运维调用。
- 节点 `DRAINING` 拒绝新呼叫但不应主动结束存量通话。

## 12. 验证

代码变更执行：

```bash
mvn -q -DskipTests compile
mvn -q test
```

涉及 Mapper/DDL 时还需解析 XML、检查查询形状和在一次性 MySQL 8 环境验证。涉及 NATS、事件、录音、WebSocket 或媒体时，必须报告外部依赖是否真实可用。

验证范围和必测用例见 [测试策略](../../docs/testing-architecture-and-test-cases.md)。真实 FreeSWITCH、SIP、双向媒体、录音完成态、TTS、认证浏览器和 Windows 交互应按 [跨电脑验收](../../docs/fcc-cross-machine-acceptance.md) 留存当次证据，不能写成长期架构事实。
