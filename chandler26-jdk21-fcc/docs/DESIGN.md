# FCC 控制面架构设计

## 1. 文档目标

本文定义 `chandler26-jdk21-fcc` 的工程边界、领域模型、运行时架构、数据一致性原则和后续实现约束。当前交付仅建立工程骨架，不实现具体业务。

FCC（FreeSWITCH Call Center）是 Java 控制面协议与领域模型。Java 服务不直接建立 FreeSWITCH ESL 长连接，而是通过 NATS 与部署在 FreeSWITCH 节点旁的 Sidecar Agent 通信。

## 2. 技术基线

| 项目 | 版本/选择 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| 构建工具 | Maven 3.6.3+ |
| ORM | MyBatis-Plus 3.5.17（包含 MyBatis 能力） |
| 数据库 | MySQL 8.0+ |
| 缓存与实时状态 | Redis 7+ |
| 消息总线 | NATS 2.x，Java Client 2.26.3 |
| 工具库 | Guava、Hutool |

MyBatis-Plus 官方 Boot 4 starter 已包含 MyBatis 集成，不再额外引入 `mybatis-spring-boot-starter`，避免 MyBatis-Spring 版本冲突。需要手写复杂 SQL 时仍使用 MyBatis Mapper XML。

## 3. 总体架构

```text
业务系统 / 坐席工作台
        │ HTTP
        ▼
┌─────────────────────┐       ┌─────────────────────┐
│ fcc-admin-starter   │       │ fcc-server-starter  │
│ 管理、配置、查询接口 │       │ 通话控制运行入口      │
└──────────┬──────────┘       └──────────┬──────────┘
           │                              │
           ▼                              ▼
┌─────────────────────┐       ┌────────────────────────────┐
│ fcc-admin           │       │ fcc-server                 │
│ 管理应用服务         │       │ Flow / Action / FCC Client │
└──────────┬──────────┘       └──────────┬─────────────────┘
           │                              │
           │ HTTP REST (:8088)            │ NATS JSON-RPC / Event
           │ (分机开户/销户/探活)          │ (呼叫控制/状态机/注册态)
           │                              ▼
           │                      ┌───────────────┐
           │                      │ NATS Cluster  │
           │                      └───────┬───────┘
           ▼                              ▼
    ┌───────────────────────────────────────────┐
    │          Go Sidecar Agent                 │
    │  - 管理面: HTTP Server (:8088)            │
    │  - 控制面: JSON-RPC 2.0 Dispatcher        │
    │  - 事件清洗: Sofia Event Normalizer        │
    └──────────────┬────────────────────┬───────┘
                   │                    │
      本地脚本调用 │                    │ Inbound ESL
      (XML落盘 +   │                    │ (API / Event Plain)
      reloadxml)   │                    │
                   ▼                    ▼
           ┌────────────────────────────────────┐
           │         FreeSWITCH 节点            │
           │ - 目录: directory/default/{ext}.xml│
           │ - Sofia SIP 引擎 (5060/5080)       │
           └────────────────────────────────────┘
```

## 4. Maven 模块职责

### 4.1 fcc-common

只放置可以被多个模块稳定复用的内容：

- 通用返回模型、分页模型和错误码；
- 跨模块值对象及稳定枚举；
- ID、时间、序列化等无业务状态工具；
- 模块间事件和接口契约。

不得放置 Spring Controller、Mapper、具体业务 Service 或启动配置，避免演变为无边界的公共模块。

### 4.2 fcc-server

FCC 核心运行模块，后续按领域组织代码：

```text
com.chandler.fcc.server
├── call           通话、Leg、Bridge 生命周期
├── flow           流程实例与步骤执行
├── routing        技能组和坐席路由
├── command        FNode.* 命令、幂等及结果
├── event          NATS 事件消费、标准化及去重
├── recording      录音元数据
├── telephony      节点、线路、号码和终端
└── infrastructure MyBatis、Redis、NATS 适配器
```

该模块不提供可执行入口，供 `fcc-server-starter` 装配。

### 4.3 fcc-server-starter

- 核心服务的 `main` 方法；
- 环境配置和运行参数；
- Actuator、日志及部署层配置；
- 未来的数据库迁移入口。

不编写业务逻辑。

### 4.4 fcc-admin

- 坐席、技能组、分机、号码和线路管理；
- 流程配置及版本管理；
- 通话、Leg、命令、事件和录音查询；
- 节点治理及运维操作；
- 统计查询和管理端 DTO。

管理模块不直接执行底层 FreeSWITCH 命令。需要控制通话时调用核心应用服务或投递明确的控制请求。

### 4.5 fcc-admin-starter

管理服务可执行入口，职责与 `fcc-server-starter` 相同，默认与核心服务使用不同端口。

## 5. 核心领域模型

### 5.1 Call

`Call` 表示一次完整的业务通话。它不等于 FreeSWITCH Channel UUID。一通业务电话在外呼、转接、咨询和三方场景下可以产生多个 Leg。

关键标识：

- `call_id`：FCC 生成的全局通话 ID；
- `biz_id`：外部工单、任务或业务单号；
- `ctrl_id`：一次控制流程的关联 ID；
- `node_id`：FreeSWITCH/Sidecar 节点，不得再用 `ctrl_id` 表达节点。

### 5.2 Leg

`CallLeg` 与一个 FreeSWITCH Channel 一一对应，以 `channel_uuid` 唯一标识。Leg 记录角色、方向、端点、号码、网关、状态和精确时间点。

典型角色：`CUSTOMER`、`AGENT`、`CARRIER`、`IVR`、`CONSULT`、`CONFERENCE`。

所有 FreeSWITCH Channel 事件必须先定位 Leg，再通过 `call_id` 聚合到业务通话。

### 5.3 Bridge

Bridge 是有时间范围的媒体连接关系。使用 `call_bridge` 和 `call_bridge_member` 表达，不能只在 Leg 上保存一个 `peer_uuid`，因为转接和会议可能产生多段或多方关系。

### 5.4 Event 与 Command

- `call_event` 是 Sidecar 上报事件的只追加事实，负责审计、去重、乱序修复和故障重放；
- `call_command` 记录控制面下发的每个 `FNode.*` 请求、响应、超时和重试；
- `command_id`、`event_id` 和 `idempotency_key` 必须具备唯一约束；
- 领域状态是 Command 与 Event 共同驱动的结果，不能只根据同步 RPC 成功判定通话已完成。

### 5.5 Flow 与 Routing

流程定义与流程执行分离：

- `flow_definition` / `flow_definition_version` 保存可发布的配置；
- `flow_instance` 表示一次通话使用的具体流程版本；
- `flow_step_execution` 记录步骤输入、输出、重试和错误；
- `call_route_attempt` 记录每次技能组、坐席或号码路由尝试。

已开始的通话固定使用启动时的流程版本，不随管理端配置变化。

## 6. 数据存储原则

### 6.1 MySQL

保存业务事实、主数据、流程版本、事件审计和最终状态。所有时间使用 `datetime(3)` 并按 UTC 写入；持续时间统一使用毫秒 `bigint`。

核心事实表不使用逻辑删除。主数据使用 `deleted_at` 表示软删除。

### 6.2 Redis

只保存可重建的实时数据：

- 坐席在线及忙闲状态；
- 节点心跳和可用容量；
- 活跃通话到节点的快速路由；
- 幂等短期键、分布式锁和限流计数；
- 号码池临时占用。

Redis 不是最终通话事实来源。状态变化必须能从数据库事件或快照恢复。

### 6.3 NATS

建议主题：

```text
fs.cmd.{nodeId}                 JSON-RPC Request/Reply (呼叫控制指令)
fs.event.{nodeId}.channel       Channel 状态事件 (START/RINGING/BRIDGE/DESTROY)
fs.event.{nodeId}.dtmf          DTMF 按键事件
fs.event.{nodeId}.recording     录音事件
fs.event.{nodeId}.registration  SIP 分机注册态事件 (REGISTERED/UNREGISTERED/EXPIRED)
fs.status.{nodeId}.heartbeat    节点心跳
```

需要重放和至少一次交付的关键事件使用 JetStream；低价值高频心跳使用 Core NATS。

## 7. 一致性与可靠性

### 7.1 幂等

- 命令以 `idempotency_key` 防止重复外呼；
- 事件以 `event_id` 防止重复消费；
- 状态更新使用状态版本或乐观锁，拒绝旧事件覆盖新状态；
- 消息处理采用“先落事件事实，再推进状态”的顺序。

### 7.2 事件乱序

事件同时保存 `event_time`、`received_at` 和节点序号。状态机仅接受合法的状态迁移；迟到事件保留在事件表，但不能回退 Call/Leg 当前状态。

### 7.3 事务边界

一次本地事务只负责：保存事件、更新领域状态、保存待发布消息。跨 NATS 与数据库不使用分布式事务，后续实现 Outbox/Inbox 保证最终一致性。

### 7.4 故障恢复

- Java 服务重启后从 MySQL 加载未终态 Call，再向对应节点查询 Channel 状态；
- Sidecar 断连期间不得盲目创建重复 Leg；
- 节点进入 `DRAINING` 后拒绝新呼叫，存量通话继续处理；
- 超时命令保持 `UNKNOWN` 或 `TIMEOUT`，等待后续事件校正，不能直接视为执行失败。

## 8. 数据安全

- 电话号码按权限脱敏，必要时列级加密；
- 录音只保存对象存储定位信息，不保存临时签名 URL；
- NATS、MySQL、Redis 凭证通过环境变量或密钥服务注入；
- 原始事件可能含 SIP 头和个人信息，需要设置保留期；
- 管理操作记录操作者、来源和操作原因。

## 9. 可观测性

统一关联字段：`trace_id`、`call_id`、`ctrl_id`、`channel_uuid`、`command_id`、`event_id`、`node_id`。

建议指标：

- 节点在线数、容量和活跃 Channel；
- NATS 请求延迟、超时率和无响应率；
- 呼叫建立成功率、应答率、平均振铃和通话时长；
- 事件积压、重复、迟到和状态机拒绝次数；
- 流程步骤失败率和重试次数。

## 10. 实施顺序与交付路线

1. 实现基础 ID、枚举和 MyBatis 公共配置；
2. 实现 Node、Call、Leg、Bridge、Event、Command 持久化；
3. 接入 NATS RPC 与事件消费，完成幂等闭环；
4. 实现 Flow、Action 和 Routing；
5. 实现坐席、技能组、号码、线路、动态配置与终端管理；
6. 实现录音、代班流转、自动外呼、未接回拨等扩展业务；
7. 建设优雅下线与凌晨自治巡检机制；
8. 最后建设统计投影与大屏报表，不以日报聚合表作为事实来源。

---

## 11. 高可用与平滑发布治理 (High Availability & Governance)

结合生产级实战沉淀，系统构建双层优雅下线与集群自愈体系：

### 11.1 服务优雅下线与流量平滑排空 (Graceful Draining)

- **控制面端点**：实现 Spring Boot Actuator 端点 `/fcc-app/enabled/{enabled}/{key}` 与 `/fcc-app/shutdown/status`；
- **状态流转机制**：维护原子状态 `UP` ➔ `PRE_DOWN` ➔ `DOWN`；
- **主动注销注册中心**：下线指令触发时，首步调用 Nacos/注册中心 Naming Service 执行 `deregisterInstance(...)`，API 网关立即切断新请求流量路由；
- **信令拦截熔断**：所有 Inbound/Outbound 事件监听器、外呼调度器首行检测 `if (isShutdown()) return;`，拒绝新业务呼叫接入；
- **存量通话排空探活 (`canDown()`)**：实时检索当前 `node_id` / `ctrl_id` 关联的活跃 `fcc_call_session`，唯有存量活跃通话数降至 0 时，`canDown()` 返回 `true`；
- **CI/CD 与 K8s 联动**：K8s `preStop` 脚本轮询 `/fcc-app/shutdown/status`，达到安全状态后才允许 Pod 终止，实现生产发布**零掉话 (Zero Dropped Calls)**。

### 11.2 分布式自治巡检与僵尸数据自愈

- **分布式锁防重**：使用 Redisson 锁（`fcc:lock:record_clean`、`fcc:lock:agent_clean`）保障多节点集群仅单一实例触发；
- **每日凌晨 02:00 定时自治巡检**：
  1. **脏通话修复**：扫描 `fcc_call_session` 中创建时间超过 2 小时仍处于非终态的脏数据，强制置为 `ABNORMAL_TERMINATED` 并补偿发布 `CallEndEvent`；
  2. **僵尸坐席登出**：扫描连续签入超过 24 小时无任何话务活动的僵尸坐席，强制调用签退注销，重置 `fcc_agent_presence`，确保次日早班 ACD 队列纯洁度；
  3. **录音与存储纠偏**：校验共享录音目录（`fcc.recording.base-dir`，本地演示默认 `~/fcc-records`）中录音文件的完整性与元数据一致性。

---

## 12. 坐席生命周期与终端合规治理 (Agent Lifecycle & Fleet Compliance)

### 12.1 PC 客户端版本发布管理

- **生命周期模型**：`fcc_client_version_release` 表纳管版本状态（`DRAFT` 草稿 ➔ `RELEASED` 已发布 ➔ `DEPRECATED` 已废弃）；
- **动态更新策略**：支持下发版本号、包下载地址、文件 MD5 校验和、发布日志及 `force_update` 强制更新标志；
- **客户端启动探活**：PC 客户端启动时请求 `GET /client/valid-version` 自动校验，按需触发静默热更新或全屏阻断式强更。

### 12.2 坐席五维硬件指纹合规审计

- **终端指纹采集**：坐席登录时，PC 客户端通过底层系统 API 采集五维硬件特征码上报并持久化到 `fcc_client_hardware_record`：
  - `mac_addr`（网卡物理地址）
  - `disk_seq`（硬盘主序列号）
  - `cpu_seq`（CPU 处理器 ID）
  - `bios_seq`（主板 BIOS 序列号）
  - `os`（操作系统版本与内核）
- **安全与风控**：管理后台建立设备绑定白名单，防范坐席私自外带客户端到非受信电脑登录，对异常设备登录触发安全告警。

### 12.3 临时代班与夜班路由流转 (Shift Substitution)

- **代班单据闭环**：通过 `fcc_agent_substitute_record` 表支持代班申请与确认（类型 `PP` 坐席对坐席 / `PG` 坐席对技能组，范围 `NIGHT_OFF` 夜班轮空，状态 `UNCONFIRM` ➔ `CONFIRM`）；
- **排队路由无感穿透**：ACD 在代班生效时间窗口内，将原本分发给原坐席的呼叫自动调度给代班坐席；
- **绩效与报表解耦**：通话记录与录音记录实际接听人（代班坐席），但工单及客户归属保留原坐席上下文，解决呼叫中心三班倒绩效统计难题。

---

## 13. 业务动态配置中心与热加载 (Dynamic Configuration & Hot Reloading)

### 13.1 多级作用域隔离 (`fcc_system_config`)

配置表划分为四大业务作用域：
- `WEB`：控制前端 UI 行为（如话后整理 ACW 倒计时、防撞单报警回溯天数、录音波形渲染采样率）；
- `BACKEND`：控制控制面调度逻辑（排队超时阈值、振铃重试次数、黑名单自动拦截开关）；
- `CLIENT`：控制 PC 客户端网络心跳周期、SIP 软电话本地缓冲区大小；
- `SYSTEM`：系统底座参数（NAS 录音存储根路径、默认 TTS 发音人参数）。

### 13.2 页面动态维护与秒级热广播

- 管理后台提供可视化的参数增删改查页面，改动后记录最后修改人与审计流水；
- 通过 NATS Subject `fcc.config.reload` 或 Redis Pub/Sub 向集群所有 `fcc-server` 与前端客户端广播热变更通知，业务参数秒级生效，彻底消除配置变更加载的重启窗口。

---

## 14. 增强话务调度与外呼风控策略 (Enhanced Routing & Outbound Protection)

### 14.1 坐席工作量均摊路由算法 (Min-Calls Balancing)

- 智能路由在原有的最长空闲优先（`LONGEST_IDLE`）和熟客记忆（`LAST_AGENT`）基础上，支持工作量均摊策略（`MIN_CALLS`）；
- 优先选择当天累积接听量最少的就绪坐席，避免快坐席过载疲劳，平滑团队工单负荷。

### 14.2 运营商外呼号码池多级频控与冷却防封

- 外呼号码按运营商（电信、联通、移动）组织为多级池化资源；
- 基于 Redis 内存维护实时可用队列，单次外呼按负载策略弹出主叫号码；
- 触发单日呼叫频控阈值时自动标记为 `COOLING` 进入冷却保护区，极大降低被运营商关停或防骚扰标记风险。

### 14.3 动态语音合成 (TTS) 与令牌桶流控保护

- 批量外呼通知导入时，严格执行手机号码格式正则强校验（`+86` 归属地清洗）；
- 语音生成接口配置 Google Guava `RateLimiter` 令牌桶限流，保护云厂商 TTS 账户并发配额；
- 以 `audio/wav` 流式响应按需下载，零本地磁盘临时文件滞留。

---

## 15. 软交换管理面与分机生命周期治理 (Management Plane & Extension Lifecycle)

针对呼叫中心日常运营中分机开户、销户必须具备**强一致性与即时同步反馈**的严苛要求，架构打破传统单一消息总线模式，推行“**控制面与管理面双平面分治**”的电信级治理方案：

```text
┌────────────────────────────────────────────────────────────────────────┐
│               双平面分治网络架构 (Dual-Plane Architecture)             │
├──────────────────────────────────┬─────────────────────────────────────┤
│ 1. 业务控制面 (Control Plane)     │ 2. 节点管理面 (Management Plane)    │
│ - 协议: NATS + JSON-RPC 2.0      │ - 协议: HTTP REST (0.0.0.0:8088)    │
│ - 目标: 极致并发、低延迟、解耦      │ - 目标: 强一致性、毫秒级同步应答     │
│ - 职责: 通话控制、动作编排、媒体  │ - 职责: 分机开户/销户、节点健康探活 │
│ - 特性: 异步发布订阅、事件归一化 │ - 特性: 本地脚本原子执行、热重载XML │
└──────────────────────────────────┴─────────────────────────────────────┘
```

### 15.1 分机增删与热重载轻量落盘引擎 (Local Script & Hot-Reload Engine)

传统方案使用 Java 直接连接 ESL 频繁执行 API 写入，或者直连 FreeSWITCH 内部数据库，容易造成数据库锁竞争或因网络抖动引起开户挂死。
本架构采用 **Go Sidecar 调度本地轻量化脚本原子操作**：

1. **落盘路径标准化**：
   - 分机配置统一由脚本写入 `/opt/homebrew/etc/freeswitch/directory/default/{extension}.xml`（或生产环境 `/etc/freeswitch/directory/...`）；
   - 脚本路径：`/opt/homebrew/etc/freeswitch/scripts/manage_extension.sh`。
2. **标准 XML 配置模板自动生成**：
   - 包含 `<user id="{ext}">`、`<params><param name="password" value="{pwd}"/></params>`；
   - 注入变量：`user_context={context}`、`effective_caller_id_number={ext}`、`outbound_caller_id_number={ext}`、`callgroup={callgroup}`；
   - 关键参数：`dial-string="{^^:sip_invite_domain=${dialed_domain}:presence_id=${dialed_user}@${dialed_domain}}${sofia_contact(*/${dialed_user}@${dialed_domain})}"`，保障 Sofia 终端呼叫路由准确无误。
3. **零停机热重载 (`reloadxml`)**：
   - 写入或删除 XML 后，脚本立即通过本地 IPC 触发 `fs_cli -x "reloadxml"`；
   - 拦截并解析 FreeSWITCH 返回的 `+OK [Success]` 状态；
   - 整体耗时仅在 **100ms ~ 300ms**，同步返回 Java 管理端成功标识。

### 15.2 SIP 注册态全生命周期事件规范 (Event.Registration)

为解决坐席终端（WebRTC 网页端 / SIP 物理话机）掉线、重连而业务系统无法实时感知的业界难题，系统实现全闭环注册态事件监听：

1. **Sofia SIP 核心事件订阅**：
   - Go Sidecar 在与 FreeSWITCH 建立 Inbound ESL 连接后，除订阅通道生命周期外，显式订阅 `CUSTOM sofia::register`、`CUSTOM sofia::unregister` 与 `CUSTOM sofia::expire`；
2. **事件清洗与标准化 (Event Normalizer)**：
   - 针对 CUSTOM 事件无 Channel `Unique-ID` 的特性，特别提取 `from-user`（或 `username`）、`from-host`（或 `realm`）、`network-ip`、`network-port`、`user-agent`、`contact`；
   - 转换为标准 JSON-RPC 2.0 通知报文：
     ```json
     {
       "jsonrpc": "2.0",
       "method": "Event.Registration",
       "params": {
         "node_id": "qiandingjundeMacBook-Pro.local",
         "user": "1007",
         "domain": "192.168.18.64",
         "status": "REGISTERED",
         "network_ip": "192.168.18.64",
         "port": "5060",
         "user_agent": "Zoiper v5.5 / BoxBox-WebRTC-SDK",
         "contact": "sip:1007@192.168.18.64:5060",
         "timestamp": 1789693905000
       }
     }
     ```
3. **NATS 广播与多端协同**：
   - Sidecar 将事件发布到 `fs.event.{nodeId}.registration`；
   - Java FCC 消费该主题后更新 Redis 分机在线态（`fcc:extension:presence:{ext}`），同时触发 WebSocket 推送至 PC 坐席工作台和运营后台；
   - 坐席下线、网络抖动超时过期（EXPIRED）即时告警，彻底避免话务分发给不可用终端导致的漏话与呼损。

### 15.3 管理面 HTTP REST 接口规范

| 请求方式 | URI 路径 | 参数说明 | 响应码与含义 | 内部执行链路 |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/extensions` | Body: `{"extension":"1088","password":"PassWord@123","context":"default","callgroup":"default"}` | `200 OK`: 开户成功<br>`400 Bad Request`: 参数缺失<br>`500 Internal`: 脚本执行失败 | Sidecar ➔ `manage_extension.sh create` ➔ 写入 XML ➔ `reloadxml` |
| **DELETE** | `/api/v1/extensions` | Query: `?extension=1088` 或 JSON Body | `200 OK`: 销户成功<br>`400 Bad Request`: 未传分机号<br>`500 Internal`: 删除失败 | Sidecar ➔ `manage_extension.sh delete` ➔ 移除 XML ➔ `reloadxml` |
| **GET** | `/api/v1/extensions` | Query: `?extension=1088` | `200 OK`: 返回分机配置是否存在及 `sofia status` 注册结果 | Sidecar ➔ `manage_extension.sh check` ➔ 检测 XML 与内存注册态 |
| **GET** | `/api/v1/health` | 无 | `200 OK`: 返回节点在线态、FreeSWITCH ping 状态与通道数 | Sidecar 内存探活与 ESL ping 校验 |




