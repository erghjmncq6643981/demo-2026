# FCC 控制面当前架构

## 1. 文档状态

本文描述 `chandler26-jdk21-fcc` 当前代码边界，核对日期为 2026-09-19。本项目是全新 FreeSWITCH 呼叫中心，不继承参考项目的旧协议、演示账户或业务兼容层。早期“仅包含骨架”的说明已经过时；未完成或未验证的能力在本文中明确列为已知缺口。

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

采用固定的绑定、呼入、呼出、自动外呼模板，保持模块化单体。2026-09-20 已实现 agent/customer/outbound 运行服务；旧 fcc-admin 工号 IVR 绑定实现、接口及 DTO 已移除，使用登录工作台的两分钟验证码与认证话机身份完成绑定。客户数据按租户与坐席隔离；调度任务先持久化，写 Call 前在同一事务锁定两分钟派发租约。过期且无 Call 的尝试原子失败，已存在 Call 的未知结果不重拨。实施与证据见 [跨电脑部署验收](../../docs/fcc-cross-machine-acceptance.md)。

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
- 分机与 IVR 绑定；
- CDR、统计、详情和录音读取；
- 回拨任务；
- 流程定义、版本、草稿和发布；仿真接口当前明确返回不可用；
- Trunk、DID、外呼号码、节点；
- 系统配置和客户端版本/硬件记录。

### 3.4 starter

`fcc-server-starter` 和 `fcc-admin-starter` 只负责启动、配置和运行时依赖装配。

## 4. 身份语义

| 字段 | 含义 |
| --- | --- |
| `call_id` | FCC 业务通话聚合 ID |
| `channel_uuid` | 单个 FreeSWITCH Channel/Leg |
| `ctrl_id` | 命令与事件关联 ID |
| `node_id` | Sidecar/FreeSWITCH 节点 |
| `bridge_uuid` | 一次媒体桥接 |
| `command_id` | 一次 FCC 命令 |
| `event_id` | 一次标准事件 |
| `flow_instance_id` | 一次流程执行 |
| `biz_id` | 外部业务关联标识 |

这些值不得互换。共享 `FccIdentifierJacksonModule` 已在 admin/server 注册：Bean 中名为 `id` 或以 `Id` 结尾的 Long/long 属性按字符串输出，时长与统计值保持数值。该规则不等于所有 Map 值、集合元素、其他命名或独立 ObjectMapper 都已覆盖；已有单元测试源码，JDK 21 编译和实际 Spring HTTP 序列化尚未验证。前端继续按不透明字符串处理身份。

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
- `flows`
- `resources`
- `configs`
- `fleet`

增长型集合必须分页；Leg、Trace、流程 JSON 和录音元数据应通过详情接口加载。当前流程列表仍嵌入全部版本和 definitionJson，且逐流程查询版本；这是尚未满足摘要/详情分离要求的例外，不是目标设计。

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

- 列表和版本查询；
- 草稿保存；
- 发布；
- 仿真能力查询（当前引擎未接入，返回失败和空轨迹）；
- 发布后 Redis 通知；
- 对 `fcc-server` 发送 HTTP reload 通知。

发布接口成功表示数据库版本切换及通知流程已执行，不等于所有运行实例或 FreeSWITCH 节点已经验证切换。运行中的呼叫是否固定版本，需要以持久 Flow Instance/Step 证据为准；当前代码不能把设计意图当作完整证明。

当前编译器 `FlowConfig` 仅支持 DID_DIRECT 的 ROUTE 阶段 DIAL_AGENT 节点，不是通用 IVR 图执行器。目标使用 `didDirectConfig.workNo`；保存、发布、编译共同拒绝旧字段及未实现路由。编译失败返回失败并保留旧定义；发布通知在 afterCommit 执行，检查 HTTP 异步结果，但尚无持久通知重试与激活看板。

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

- Sidecar 事件现有稳定源 `event_id` 与落盘 outbox；事件按接收顺序写入，Java Inbox 去重。处理中的中断及失败事件仍需业务对账；
- 录音事件已统一为 `Event.Recording`，分类为 `record`；真实完成态、时长和文件大小仍需 Sidecar/FreeSWITCH 联调验证；
- NATS 事件使用 FCC_EVENTS JetStream；fcc-control durable 每次处理一条并显式 ACK，部署前必须创建流；
- 当前只支持一个活跃 fcc-server，不能把共享 durable 等同安全的多实例会话处理；
- 命令重试尚未形成跨请求稳定的业务幂等键；
- 消费前从 MySQL 分页恢复固定模板会话；尚缺与真实 FS 话道快照的自动对账；
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

2026-09-20：Java 编译和定向 SQL/边界测试通过，真实 MySQL 验证覆盖租户隔离、乐观锁、绑定占用和任务原子回填。Sidecar 已用本机真实 JetStream 验证 outbox；Java 全量测试有 9 个上下文启动错误（本机 JDK 回环连接创建失败）。真实媒体、录音、认证浏览器与 Windows 交互尚未验收。部署的新配置与顺序见 [跨电脑验收](../../docs/fcc-cross-machine-acceptance.md)。
