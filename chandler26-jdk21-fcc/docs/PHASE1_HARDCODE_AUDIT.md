# FCC 控制面 · 硬编码治理与前端一期对齐梳理

> 梳理对象：`chandler26-jdk21-fcc`（Java 21 / Spring Boot 4 多模块控制面）
> 对齐基准：`chandler26-fcc-admin-web`(:8000)、`chandler26-fcc-client-web`(:8888)、`fswitch-web`(:8008) 三个前端**一期（原型阶段）的真实调用面**
> 梳理方式：只读代码审计，未改动任何源码

---

## 0.1 落地情况（2026-09-19 更新）

本审计的处置建议已按"真实优先"原则落地，与本文结论有出入之处以本节为准：

| 事项 | 处置结果 |
| --- | --- |
| `TelephonyWsSimulationController`（仿真弹屏 / 仿真挂机 / 在线态势） | **已整体删除**。弹屏不再由外部接口灌入，"谁来弹屏"只由真实坐席路由决定 |
| C 类假弹屏数据（C1 / C2） | **已删除**。弹屏载荷改为由 `ScreenPopService` 依据通话上下文 + `fcc_call_session` / `fcc_call_recording` / `fcc_flow_definition` / `fcc_agent` 真实事实装配，取不到即留空；DTO 精简为 14 个可追溯字段 |
| E1 WS 消息类型错位 | **已修复**。后端统一为 `SCREEN_POP` / `CALL_ANSWERED` / `CALL_HANGUP` / `CHANNEL_READY`，前端按同一套 code 收发；心跳改用 `HEARTBEAT_PING` / `HEARTBEAT_PONG` |
| B1 工号默认兜底 | **已清理**。`AgentWebSocketHandler` 缺 `workNo` 直接拒链；推送目标一律取自会话真实接待坐席，取不到则不下发 |
| A6 录音路径写死 `/mnt/nas/fcc/records` | **已配置化**，默认 `${user.home}/fcc-records`，生产用 `FCC_RECORDING_BASE_DIR` 指向共享挂载点 |
| 前端本地伪造话单（`cdrStore.prependRecord`） | **已删除**。挂机后重新拉取后端真实话单，不再在列表里插入前端拼装的记录 |
| F2 管理端流程预演编造工号（902987 / 901415） | **仍保留**。该功能是管理端"流程预演"工具，输出明确标注为模拟轨迹且不落事实表，与话务弹屏无关，故未纳入本次改动 |

---

## 0. 结论摘要

当前后端**功能完成度约 80%**，但存在两类结构性问题，二者叠加导致"演示能跑、联调必错"：

1. **硬编码密度过高**：`"901001"` 在主代码出现 **30 处**、`"1007"` 出现 **18 处**，且散落在 7 个文件里，没有一处走配置。任何一次「换个人演示」都要改代码重编译。
2. **前后端契约未对齐**：WebSocket 消息枚举、CDR 状态枚举、flowKey 三处命名对不上，其中 WS 枚举错位是**功能性失效**（后端推的弹屏与挂机，前端全部收不到）。

治理原则建议：**一期的目标是"三端能真跑通"，不是"把 33 张表全部填满"**。
因此本次梳理把每一项分成四类处置：`配置化` / `下沉种子数据` / `一期删除或降级` / `契约修复`。**不建议在一期做全量重构**，只做"打通演示链路"所必需的那部分。

---

## 1. 一期范围界定：三个前端真正调用了什么

### 1.1 后端控制器全景 vs 前端覆盖

| 后端控制器 | 路径前缀 | admin-web | client-web | fswitch-web |
|---|---|:---:|:---:|:---:|
| `AuthController` | `/api/admin/auth` | ✅ | ✅ | — |
| `AgentController` | `/api/admin/agents` | ✅ 全部 | ✅ 白名单/端点/切换 | — |
| `ExtensionController` | `/api/admin/extensions` | ✅ | — | — |
| `CallCdrController` | `/api/admin/cdrs` | ✅ | ✅ | — |
| `CallbackTaskController` | `/api/admin/callbacks` | ✅ | ✅ | — |
| `CallRecordingController` | `/api/admin/recordings` | ✅ 音频流 | ✅ 音频流 | — |
| `FlowDefinitionController` | `/api/admin/flows` | ✅ 保存/发布 | — | — |
| **`TelephonyResourceController`** | `/api/admin/resources` | ❌ 未调用 | ❌ | — |
| **`SystemConfigController`** | `/api/admin/configs` | ❌ 未调用（页面是静态假数据） | ❌ | — |
| **`ClientFleetController`** | `/api/admin/fleet` | ❌ 未调用（同上） | ❌ | — |
| `TelephonyCallController` | `/api/telephony/call` | — | ✅ 7 个动作 | — |
| `TelephonyWsSimulationController` | `/api/telephony/ws` | — | ✅ 仿真弹屏 | — |
| `AgentWebSocketHandler` | `ws://:8085/ws/agent` | — | ✅ | — |
| *(Sidecar 直连)* | `http://:8088/api/v1/*` | — | — | ✅ 全量 |

**关键结论**：`TelephonyResourceController`、`SystemConfigController`、`ClientFleetController` 这三个（合计约 20 个接口）**一期前端零调用**。它们是二期能力，当前属于"写了但没接线"的状态。

### 1.2 三端 API 基址与端口契约（已核对）

| 前端 | 端口 | API 基址 | 代理目标 | WS |
|---|---|---|---|---|
| admin-web | 8000 | `/api/admin` | `127.0.0.1:8089` | 无 |
| client-web | 8888 | `/api/admin` + `/api/telephony` | `8089` / `8085` | 直连 `ws://{host}:8085/ws/agent`（绕过代理） |
| fswitch-web | 8008 | `/api/v1` | `127.0.0.1:8088`（侧车） | 直连 `/api/v1/telephony/ws/console-logs` |

---

## 2. 硬编码清单（按处置类别分组）

### A 类 · 环境与路径硬编码 → **必须配置化**

| # | 位置 | 硬编码内容 | 问题 |
|---|---|---|---|
| A1 | `FlowConfig.java:195,244,245,327,328` | 5 个 IVR 音频绝对路径 `/Users/chandler/…/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/*.wav` | **跨工程指向上代仓库**，换机器必挂；应改为 `${fcc.sounds.dir}` + 相对文件名 |
| A2 | `FccProperties.java:30` + `fcc-server-starter/application.yml:32` | `defaultNodeId = qiandingjundeMacBook-Pro.local` | 个人主机名进入代码与配置默认值 |
| A3 | `FccProperties.java:25,45` | `nats://127.0.0.1:4222`、`http://127.0.0.1:8088` | yml 已有 `${NATS_URL}` 覆盖，但 Java 侧仍有兜底默认值，双份真相 |
| A4 | `FlowDefinitionService.java:328` | `http://127.0.0.1:8085/api/telephony/call/flow/reload` | **管理端硬编码调运行端端口**，且未走配置/服务发现；Redis 广播已发过一次，这条是重复通道 |
| A5 | `CallRecordingController.java:35` | `DEFAULT_DEMO_DIR = /Users/chandler/…/demo-2026/recordings` | 演示兜底录音目录写死在本机 |
| A6 | `RecordActionExecutor.java:35` | `/mnt/nas/fcc/records/{callUuid}.wav` | 生产路径写死，本地不可用 |
| A7 | `FccEventListener.java:669` | `/data/recordings/{uuid}.wav` | **与 A6 不是同一个目录**，录音元数据与真实落盘路径不一致 |
| A8 | `FccEventListener.java:264,284` | `nodeId("fcc-node")` | 假节点名，与真实 `node_id`（Sidecar 上报）矛盾，CDR 里节点维度不可用 |
| A9 | `TelephonyCallController.java:186` | `"sofia/gateway/external/"` | 中继网关名写死，多网关场景失效 |

### B 类 · 坐席工号 / 分机号魔法值 → **配置化 + 数据驱动**

| # | 值 | 出现位置（主代码） | 用途 |
|---|---|---|---|
| B1 | `"901001"` | `AgentWebSocketHandler:53`、`TelephonyCallController:122,211,272,340`、`FccEventListener:192,234,243`、`CallPersistenceService:78`、`FlowDefinitionService:378`、`TelephonyWsSimulationController:42,76` | 主管工号默认兜底 / WS 推送目标 / 弹屏收件人（**12 处，共 30 次命中**） |
| B2 | `"1007"` | `FlowConfig:138,139,157,168,210,276`、`DialAgentActionExecutor:40`、`FccEventListener:163,507`、`AgentService:141,769,842`、`AuthService:136` | 默认坐席分机 / 新建坐席时**无条件塞一条 SIP 绑定**（**13 处，共 18 次命中**） |
| B3 | `"1008"` | `DialGuestActionExecutor:40,41`、`FlowConfig:265,313` | 双向外呼的默认客户/坐席号 |
| B4 | `"1017"` | `TelephonyCallController:378` | 转接兜底目标 |
| B5 | `"9000"/"8000"/"9999"` | `FccEventListener:138` | **靠被叫号码猜"这是呼入"**，而非用 `direction` 字段判定 |
| B6 | `"1001"` | `FlowConfig:139` | DID 直达的工号→分机映射表，写死在 if-else 里 |
| B7 | `"13800000001"` | `AgentService`（2 处） | 手机接听兜底号 |
| B8 | `"902987"` / `"901415"` | `FlowDefinitionService` 仿真分支 | **编造的工号**（鹏飞/舒欣），与白名单 901001–901009 **对不上**，仿真结果与真实数据自相矛盾 |

> **B 类的根因**：`fcc_agent`、`fcc_agent_endpoint_binding`、`fcc_extension` 三张表其实已经建好且 `AgentService` 提供了完整 CRUD，但**话务链路（FlowConfig / FccEventListener / TelephonyCallController）没有查表，而是用了字面量**。只有 `FccEventListener:509-539` 那一段 IVR 路由做了动态查询——说明模式是通的，只是没铺开。

### C 类 · 业务假数据（CRM 弹屏） → **一期保留但集中收口**

| # | 位置 | 内容 |
|---|---|---|
| C1 | `FccEventListener.java:196-219` | 一整套写死的来电弹屏：`广东深圳 · 中国移动`、`021-60882100`、`箱箱全国客服热线`、`保险投保服务一组`、`CRM-CUST-880192`、`张建国`、`深圳港供应链物流有限公司`、`¥368,000 / 年`、`TK-8921`、`陈松(901002)`、`media.boxbox.com` 录音地址、整段推荐话术 |
| C2 | `TelephonyCallController.java:164-173` | 外呼弹屏假数据：`智能外呼专线`、`021-60882100`、`箱箱智能客服热线`、`长三角物流供应链` |
| C3 | `TelephonyWsSimulationController.java:114-142` | `buildDefaultScreenPop()` **把 C1 的数据又抄了一遍**（号码换成 `19166340294`），三处各写一份 |
| C4 | `FccEventListener.java:162-163` | `enableSurvey="true"`、`agentExt="1007"` 无条件写死 |

> C 类的定位要澄清：一期**确实需要**假数据来支撑演示（前端弹屏 UI 依赖这些字段）。问题不是"有假数据"，而是**同一份假数据在 3 个文件里各维护一份**。建议抽成一个 `DemoScreenPopFactory` 或直接落 `fcc_system_config`（`CLIENT` 作用域），一处维护。

### D 类 · 认证与「9 人白名单」 → **优先级最高的重构项**

这一块是本次梳理的重点，也是目前**最有争议的设计**。

**D1 · 白名单硬编码**：`AgentWhitelistEnum.java` 把 9 位成员（钱丁君 901001 主管 + 陈松/舒欣/森林/志行/苏宁/亚峰/张闯/鹏飞 901002–901009）**以枚举形式写死在 Java 里**，包含姓名、职务、预留工号、是否主管。

**D2 · 白名单事实上没在"限制"**：`AgentService.java:705-714` 的 `validateWhitelist()` 名字叫校验，实际是：
```java
if (AgentWhitelistEnum.isWhitelisted(name)) {
    log.info("👤 录入法定白名单坐席成员: {}", name);
} else {
    log.info("👤 录入业务扩展自定义坐席人员: {}", name);   // ← 非白名单只换了一句日志
}
```
两个分支都是 `log.info`，**不抛异常、不拦截**。所以"严格限定仅允许此 9 位合法成员录入"的注释与行为不符。

**D3 · 白名单反而是认证旁路**：`AuthService.java:79-86`
```java
if (agentEntity == null && matchedWhitelist == null) {
    throw new IllegalArgumentException("坐席工号不存在: " + username);
}
```
只要工号命中枚举（901001–901009），**数据库里没有坐席档案也能登录**，`realName` 从枚举取。

**D4 · 硬编码口令**：
- `AuthService.java:52-55`：超管 `admin` / `admin123` 写死在代码里
- `AuthService.java:93-95`：**全体坐席共用同一个密码 `123456`**，且错误提示直接把密码告诉调用方

**D5 · 与前端重复**：`client-web/LoginView.vue`、`LoginModal.vue` 里同样硬编码了 901001/123456 与 9 人名单——**同一份白名单前后端各存一份**。

**建议处置**：
- 一期：把 `agent` / `agent_group` 表灌入这 9 人作为**种子数据**（`Import` SQL 或 `data.sql`），枚举退化为"初始化模板"或直接删除；
- `AuthService` 改为**只认数据库**，删除白名单旁路；密码改为 `password_hash` 字段（一期可先用固定盐 MD5/SHA256）；
- `admin/admin123`、`123456` 移入 `application-local.yml` 或环境变量；
- 前端登录页的 9 人名单改为调 `GET /agents/whitelist`（或新的 `/agents/list`）动态渲染。

### E 类 · 前后端契约错位 → **必须修，且是"功能失效级"**

| # | 契约点 | 后端 | 前端 | 后果 |
|---|---|---|---|---|
| **E1** | WS 消息类型 | `WsMessageTypeEnum`：`INCOMING_CALL_RINGING` / `CALL_ANSWERED` / `CALL_HANGUP` / `AGENT_PRESENCE_CHANGE` / `CALL_CONTROL_ACTION` / `HEARTBEAT_PING` / `HEARTBEAT_PONG` | `types/telephony.ts:38`：`PING` / `PONG` / `SCREEN_POP` / `CALL_STATE_CHANGE` / `HANGUP_ALERT`；`App.vue:176,178` 只判 `SCREEN_POP`、`HANGUP_ALERT` | **来电弹屏、接通、挂机三条推送前端全部丢弃**。这是当前最影响演示效果的一处 |
| **E2** | 心跳 | 只认 `HEARTBEAT_PING` | 发 `{type:'PING'}` | 后端不回 pong，前端 RTT 永远停在初始值 `5ms` |
| **E3** | CDR 状态 | `ANSWERED` / `NO_ANSWER`（`CallPersistenceService:123`），注释另有 `BRIDGED/COMPLETED/BUSY` | `MISSED`/`TIMEOUT`/`BUSY`/`REJECTED`/`CANCELLED`/`NORMAL_END`/`ANSWERED` | 前端 6 个状态里只有 1 个能命中，其余全部兜底成"已接通"，**报表接通率失真** |
| **E4** | flowKey | `FlowConfig` 只识别 `FLOW-INBOUND` / `FLOW-OUTBOUND` | `IvrFlowView.vue:1129` 会发 `FLOW-PHONEDIRECT`（画布里 code 却是 `FLOW-PHONE-DIRECT`） | 第三种流程保存/热加载必然失败，且前端自身两处拼写就不一致 |
| **E5** | WS 地址 | — | `websocketService.ts:22` 写死 `ws://{hostname}:8085` | 绕过 Vite 代理，跨机部署/HTTPS 环境不可用 |
| **E6** | 查询参数名 | `AgentQueryReq`：`agentName` / `roleCode` | `agentApi.list` 传 `realName` / `role`，且实际未传（前端内存过滤） | 当前不至于报错，但一旦后端启用筛选就会静默失效 |

### F 类 · 一期未接线 / 死代码 → **一期降级或标注**

| # | 内容 | 建议 |
|---|---|---|
| F1 | `TelephonyWsSimulationController` 3 个仿真接口（`simulate-screen-pop` / `push-hangup` / `sessions`） | 一期**保留**（演示刚需），但加 `@Profile("!prod")` 或 `@ConditionalOnProperty` |
| F2 | `FlowDefinitionService.simulateFlow()` 编造工号 902987/901415 | 改为从 `fcc_agent` 取真实坐席，否则仿真日志与真实数据打架 |
| F3 | `SystemConfigController`（`/configs`）+ admin-web 的 `sysvars` Tab 静态数据 | 二者对不上：后端有接口没被调，前端有页面没接口。一期二选一：要么接上，要么页面标注"二期" |
| F4 | `ClientFleetController`（`/fleet`）+ admin-web 的 `clients` Tab 静态数据 | 同上 |
| F5 | `TelephonyResourceController`（`/resources`，线路/号码池/终端） | 前端一期零调用，属二期 |
| F6 | `AgentSubstituteRecord`（代班流转） | 前端一期零调用 |
| F7 | 录音兜底：`CallRecordingController` 找不到文件时返回 `rec-default.mp3` | 演示可用，但会把"无录音"包装成"有录音"，报表侧要能区分 |
| F8 | `fcc_outbox_event` 表 | **建了但代码从未写入**（见 §4），一期可先删或标注保留 |

---

## 3. 处置决策表（按优先级）

| 优先级 | 事项 | 类别 | 动作 | 涉及文件数 |
|---|---|---|---|---|
| **P0** | WS 消息枚举对齐（E1/E2） | 契约 | 统一为一套枚举，后端优先（`INCOMING_CALL_RINGING` 等），前端改判定 + 心跳值 | 后端 1 / 前端 3 |
| **P0** | 白名单退出认证链路（D2/D3/D4） | 安全 | 删白名单旁路，口令外置，9 人转为种子数据 | 3 |
| **P0** | IVR 音频路径改配置（A1） | 环境 | 新增 `fcc.sounds.dir`，5 处字面量替换 | 2 |
| **P1** | 工号/分机魔法值下沉（B1–B4、B6、B7） | 数据 | 话务链路改为查 `fcc_agent` / `fcc_agent_endpoint_binding`，仅保留"取不到时才用兜底" | 7 |
| **P1** | CDR 状态枚举对齐（E3） | 契约 | 后端收敛为 `ANSWERED / NO_ANSWER / BUSY / MISSED`，前端按此改 | 后端 2 / 前端 1 |
| **P1** | 假弹屏数据收口（C1–C3） | 整洁 | 抽 Factory 或落 `fcc_system_config`，三处合并为一处 | 3 |
| **P1** | flowKey 统一（E4） | 契约 | 定为 `FLOW-INBOUND / FLOW-OUTBOUND / FLOW-PHONE-DIRECT`，前后端同步 | 2 |
| **P2** | 录音路径统一（A6/A7） | 环境 | 统一为 `fcc.recording.dir`，元数据与实际落盘一致 | 2 |
| **P2** | 节点名 `fcc-node` → 真实 node_id（A8） | 数据 | 从 Sidecar 事件取 `node_id` | 1 |
| **P2** | 超管/坐席口令外置（D4） | 安全 | 移入 profile 或 env | 1 |
| **P2** | 未接线模块标注或启用（F3/F4） | 范围 | 二选一，避免"看起来能做其实没通" | 4 |
| **P2** | 呼入判定改用 `direction`（B5） | 逻辑 | 删 `9000/8000/9999` 魔法号码 | 1 |
| **P3** | 仿真接口加 profile 隔离（F1） | 运维 | `@Profile` 或开关 | 1 |

---

## 4. 顺带发现的非硬编码问题（一期打通会撞上）

1. **事件消费无去重**：`FccEventListener` 每次生成新的 `event_id`（`IdUtil.getEventId()`）再入库，**NATS 重投会产生重复事实**，违背 `AGENTS.md` "去重前置"的硬要求。
2. **`fcc_outbox_event` 建表但零写入**，事务性发件未落地。
3. **流程执行历史不落库**：`flow_instance` / `flow_step_execution` 两张表存在，但动作轨迹只写 `DefaultActionExecutorsManager` 的**内存** `FlowCtrlRecord`，重启即丢。
4. **同表双实体**：`fcc_call_session` 在 fcc-server 和 fcc-admin 各有一套 Entity（admin 侧 35 字段），字段集不一致，容易互相覆盖。
5. **无迁移工具**：没有 Flyway/Liquibase，`docs/fcc-schema.sql` 是静态文件，33 张表靠手工执行。
6. **`sa-token-spring-boot3-starter` 1.39.0 + Spring Boot 4.1.1** 版本组合存疑，建议一期先跑一次登录验证。
7. **23 个 Mapper XML 全为空**，全部走 MyBatis-Plus Wrapper——能跑，但复杂查询（如 CDR 多表关联、报表聚合）会很难写。

---

## 5. 一期建议执行顺序

```
Step 1（半天）  契约对齐三件套
                ├─ WS 消息枚举前后端统一  → 演示链路立刻活过来
                ├─ CDR 状态枚举统一        → 报表接通率变准
                └─ flowKey 统一            → IVR 第三流程可保存/热加载

Step 2（1 天）  安全与配置收敛
                ├─ 白名单退出认证 + 9 人转种子 SQL
                ├─ admin123 / 123456 外置
                └─ IVR 音频路径改 fcc.sounds.dir

Step 3（1~2 天）数据驱动改造
                ├─ 话务链路工号/分机查表（B 类 7 个文件）
                ├─ 假弹屏数据收口到一处
                └─ 呼入判定改用 direction

Step 4（1 天）  补齐"能跑通"的底座
                ├─ 事件 event_id 去重
                ├─ 录音路径统一
                └─ 引入 Flyway，把 docs/fcc-schema.sql 变成 V1__init.sql
```

**一句话总结**：一期不要碰 33 张表和 67 个接口，**先把上面 P0+P1 的 11 件事做完**，三端就能从"各自演示"进入"真联调"。白名单是最该先动的一块——它现在既不限制录入、又绕过认证、还前后端各存一份，属于负资产。

---

## 附录 · 证据索引

| 分类 | 关键文件 |
|---|---|
| 流程编排硬编码 | `fcc-server/…/flow/FlowConfig.java`、`flow/action/executor/*.java`（7 个） |
| 事件与假数据 | `fcc-server/…/event/FccEventListener.java` |
| 话务控制硬编码 | `fcc-server/…/websocket/controller/TelephonyCallController.java` |
| 仿真与假弹屏 | `fcc-server/…/websocket/controller/TelephonyWsSimulationController.java` |
| 白名单与口令 | `fcc-admin/…/model/enums/AgentWhitelistEnum.java`、`service/AuthService.java`、`service/AgentService.java`、`controller/AgentController.java` |
| 流程发布热通知 | `fcc-admin/…/service/FlowDefinitionService.java` |
| 录音路径兜底 | `fcc-admin/…/controller/CallRecordingController.java` |
| WS 契约 | `fcc-common/…/enums/WsMessageTypeEnum.java` ↔ `client-web/src/services/websocketService.ts`、`types/telephony.ts`、`App.vue` |
| 前端一期调用面 | `admin-web/src/api/*.ts`、`client-web/src/api/*.ts`、`fswitch-web/src/api/telephony.ts` |
