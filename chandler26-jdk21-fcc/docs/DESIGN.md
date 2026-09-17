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
           │                              │ JSON-RPC / Event
           ├──── MySQL / Redis ───────────┤
                                          ▼
                                  ┌───────────────┐
                                  │ NATS Cluster  │
                                  └───────┬───────┘
                                          ▼
                                  Go Sidecar Agent
                                          │ ESL
                                          ▼
                                      FreeSWITCH
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
fs.cmd.{nodeId}                 JSON-RPC Request/Reply
fs.event.{nodeId}.channel       Channel 状态事件
fs.event.{nodeId}.dtmf          DTMF 事件
fs.event.{nodeId}.recording     录音事件
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

## 10. 后续实施顺序

1. 实现基础 ID、枚举和 MyBatis 公共配置；
2. 实现 Node、Call、Leg、Bridge、Event、Command 持久化；
3. 接入 NATS RPC 与事件消费，完成幂等闭环；
4. 实现 Flow、Action 和 Routing；
5. 实现坐席、技能组、号码、线路和终端管理；
6. 实现录音、自动外呼、未接回拨等扩展业务；
7. 最后建设统计投影，不以日报聚合表作为事实来源。



