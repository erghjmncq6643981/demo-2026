# FCC 控制面当前架构

## 1. 文档状态

本文描述 `chandler26-jdk21-fcc` 当前代码边界，核对日期为 2026-09-20。本项目是全新 FreeSWITCH 呼叫中心，不继承参考项目的旧协议、旧流程定义、演示账户或业务兼容层，也不引入租户模型。未完成或未验证的能力在本文中明确列为已知缺口。

跨工程入口见 [产品设计总览](../../docs/fcc-product-design.md)，源码对齐证据见 [前后端契约检查](../../docs/fcc-contract-alignment.md)。本文描述现状；总览中的待完善清单是验收目标，不代表已实现。

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

### 2.1 已确认的目标职责（待分阶段实现）

fcc-server 统一拥有话机绑定运行流程、呼入、呼出、自动外呼调度、客户资料和 Windows 弹屏业务。自动外呼和客户资料不下沉 Sidecar，也不由客户端维护业务事实。fcc-admin 保留基础配置及管理入口，通过明确 API/消息边界协作，不新增对 fcc-server 实现模块的直接依赖。

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
- `infrastructure.persistence`：Call、Leg、Bridge、Event、Command 和 Recording 持久化；
- `recording`：共享录音路径；
- `websocket`：话务 REST、Agent WebSocket 和来电弹屏。

### 3.3 fcc-admin

当前包含：

- 登录、当前用户和密码操作；
- 管理员与坐席账户；
- 客服组、组员、终端绑定和代班；
- 分机配置；话机拨号输入工号的运行绑定由 fcc-server 负责；
- CDR、统计、详情和录音读取；
- 回拨任务；
- 流程动作目录、固定模型、分页摘要、版本详情、草稿和发布；
- Trunk、DID、外呼号码、节点；
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
4. `FccClient` 请求 `fs.cmd.{nodeId}` 并等待 Sidecar 同步应答。
5. Sidecar 调用 FreeSWITCH。
6. Sidecar 发布 `Event.Channel`、`Event.DTMF`、`Event.Recording` 或 `Event.Registration`。
7. `FccEventListener` 更新会话、持久化事实、触发后续动作并推送 Agent WebSocket。
8. 坐席端以业务 WebSocket和 SIP Session 对账最终状态。

同步 RPC 成功不是振铃、接通、桥接或录音成功的最终证据。

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

## 8. 流程版本

管理端支持流程：

- 分页流程摘要和分页版本摘要；
- 单版本完整定义按需查询；
- 公共动作目录和四个固定系统模型查询；
- 草稿保存；
- 发布；
- 按通话查询实际经过的阶段、action、指令、事件和结果；
- 发布后 Redis 通知；
- 对 `fcc-server` 发送 HTTP reload 通知。

发布接口成功表示数据库版本切换完成，运行端状态返回 `PENDING`；这不等于所有运行实例或 FreeSWITCH 节点已验证切换。新通话在创建 Flow Instance 时固定版本快照，存量通话不随发布改写。

可编辑流程只支持 `routeMode=IVR` 的呼入固定阶段：`ENTRY -> MENU -> BRANCH -> ROUTE -> BRIDGE -> CONNECTED -> END`。编辑者只能修改菜单媒体、收号超时、单键 if/else 分支、坐席/技能组目标、排队时限和未接通处理。不接受旧 `DID_DIRECT`、任意 Java 类/方法、脚本、表达式或动态 URL。

`FlowActionType` 是 admin/server 共用动作目录，每个动作明确归属 FNode 指令、内部业务方法或第三方接口。FNode 方法和事件方法/字段由 `FNodeMethod`、`FccEventMethod`、`FccEventField` 等公共对象定义。第三方动作只能使用服务端命名白名单中的 HTTPS 端点和稳定幂等键。发布通知在 afterCommit 执行，但尚无持久通知重试与激活看板。

## 9. 录音

`fcc-server` 根据共享录音根目录生成并下发文件路径，Sidecar/FreeSWITCH 写文件，Java 保存录音元数据，`fcc-admin` 提供授权读取接口。

两个服务必须配置相同的 `FCC_RECORDING_BASE_DIR`。管理端只允许读取根目录范围内的文件；生产环境不得开放任意路径读取。

## 10. 当前可靠性边界

已经实现：

- FNode 同步请求超时；
- Call/Leg/Command/Event/Recording 持久化；
- 部分重复写入保护；
- WebSocket 心跳响应和多 Session 推送；
- Sidecar 节点目标参数；
- 共享录音路径范围校验。

仍需作为已知缺口处理：

- Sidecar 事件现有稳定源 `event_id` 与落盘 outbox；事件按接收顺序写入，Java Inbox 去重。明确处理失败会 NAK 并最多重新领取 5 次，已完成的重复事件直接 ACK；遗留 `PROCESSING` 或耗尽重试的 `FAILED` 转为 `UNKNOWN`，等待人工或恢复任务对账；
- 录音事件已统一为 `Event.Recording`，分类为 `record`；真实完成态、时长和文件大小仍需 Sidecar/FreeSWITCH 联调验证；
- NATS 事件使用 FCC_EVENTS JetStream；fcc-control durable 每次处理一条并显式 ACK，部署前必须创建流。业务副作用与 Inbox 状态仍不是同一事务，本轮也没有真实 JetStream 重投证据；
- 当前只支持一个活跃 fcc-server，不能把共享 durable 等同安全的多实例会话处理；
- 命令重试尚未形成跨请求稳定的业务幂等键；
- 消费前从 MySQL 分页恢复固定模板会话；已有 ChannelSnapshot 双方持续缺失对账，失败快照不视为挂机，单边残留和桥接重建仍需补齐；
- 流程发布使用 Redis 与 HTTP best-effort 通知，不是事务性发布；
- Agent WebSocket 已通过令牌在线核验坐席身份；身份服务故障时拒绝收发，尚需真实环境验证及性能评估；
- 共享 Jackson 标识符模块已加入，但命名外字段、Map 和实际 HTTP 输出仍需完整契约验证；
- Dial Job 已有持久领取、频控、时段、暂停/取消、逐次结果及原子批量回填；真实话务与未知结果恢复尚未完整验收。

挂机/保持/DTMF/转接已校验本人 callId、节点和底层响应；返回 ACCEPTED 而非最终状态，错误/超时不再伪造成功。班长四类干预均明确返回 501，前端禁用。保持媒体完成态和完整转接生命周期仍需联调。

## 11. 安全与运维

- 数据库、Redis、NATS、Sidecar、SIP 和录音配置从部署环境注入。
- 管理操作需要后端授权；隐藏按钮不能替代权限校验。
- 电话号码、录音、协议原文和文件路径属于敏感数据。
- 强拆、流程发布、分机/中继变更和录音访问需要业务审计。
- `FNode.NativeAPI` 只允许受限运维调用。
- 节点 `DRAINING` 拒绝新呼叫但不应主动结束存量通话。

## 12. 验证

代码变更执行：

```bash
mvn -q -DskipTests compile
mvn -q test
```

涉及 Mapper/DDL 时还需解析 XML、检查查询形状和在一次性 MySQL 8 环境验证。涉及 NATS、事件、录音、WebSocket 或媒体时，必须报告外部依赖是否真实可用。

2026-09-20 本轮：JDK 21 编译、Flow Studio/Action Executor 定向测试和前端构建按交付时结果记录。全量 Java 测试需连接本地 NATS；若 NATS 不可用，不宣称全量通过。本轮没有连接用户 MySQL，也没有验证真实 FreeSWITCH、双向媒体、录音、认证浏览器或 Windows 交互。

### 运行端补充接口

- `GET/POST /api/telephony/agent-state`：本人坐席状态，通话占用由服务端维护。
- `GET/POST /api/telephony/calls/{callId}/summary`：当前坐席已结束通话的小结；提交幂等，不释放较新通话占用。
- `GET /api/telephony/callbacks`、`POST /api/telephony/callbacks/{id}/call`：本人或未分配回拨摘要、原子领取并创建调度任务。
- `/ws/agent` 的 `SCREEN_POP_RECEIPT`：服务端按认证工号与通话保存展示事实，不将展示当作接听。
