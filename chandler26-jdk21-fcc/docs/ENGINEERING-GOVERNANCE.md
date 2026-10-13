# FCC 工程治理交付记录

记录日期：2026-10-04。范围为 FCC 后端、管理前端、坐席前端；这是工作区实现与当次验证记录，不是部署验收声明。

## 1. 边界与结果

本轮保持模块、REST/DTO、数据库结构及 NATS/Sidecar 协议不变，不引入框架或 SDK。保留原事务与外部调用顺序，避免把结构拆分变成话务策略重写。小范围行为修正仅包含：迟到示闲通知保护、订阅/计时器/调度器的所有者清理。

| 原入口 | 治理前行数 | 治理后行数 | 责任分配 |
| --- | ---: | ---: | --- |
| OutboundCallService | 1086 | 631 | 入口、会话锁与事件路由；媒体、通知与收尾分别交给协调器 |
| CallPersistenceService | 882 | 741 | Call/Leg/Bridge 持久化；命令意图、回执和关联校验交给 CommandAuditPersistence |
| AgentService | 926 | 730 | 坐席管理；组配置与成员管理交给 AgentGroupManagementService |
| 坐席 App.vue | 252 | 139 | 页面壳与快捷键；登录、REST 初始化、WS/SIP 订阅交给 useAgentRuntime |
| AgentManageView.vue | 1064 | 728 | 模板展示；表单、查询、提交交给 useAgentManagement |
| MonitorView.vue | 1105 | 595 | 展示；报表状态交给 useMonitoringReport，请求生命周期交给 useReportData |
| CallExecutionPanel.vue | 1404 | 202 | 旅程展示；只展示执行证据，移除无证据成功描述与大文件豁免 |
| CdrReportView.vue | 1016 | 841 | 列表与查询状态；详情展示提取为 CdrDetailDialog |

行数只用于触发评审，不代表质量评分；纯模板仍可能较长，不为了达标增加空层次。

## 2. 所有权和运行边界

- OutboundMediaCoordinator 处理拨号、桥接、坐席媒体与接通更新；OutboundNotificationService 处理通知提示和通知命令结果；OutboundLifecycleCoordinator 处理结束与话后阶段。终态映射收敛到 OutboundTerminalPolicy，模板常量集中在 OutboundRuntimeSupport。
- CommandAuditPersistence 接管原命令事务边界：意图写入仍为 REQUIRES_NEW，回执与结果仍为 REQUIRED。原持久化入口仅代理，避免增加第二个独立事务。
- AgentGroupManagementService 拥有组与成员规则，AgentService 保留已有 API 和组合用例。原组删除 SQL 循环未优化，不宣称已批量化。
- 结构治理阶段为 CallTransferService 增加 watchdog 销毁清理；后续可靠性整改已增加持久截止时间与控制阶段、重启检查和未知结果保留。完整转接尝试事实及 NativeAPI 路径治理仍未完成，详见 [运行可靠性整改](RUNTIME-RELIABILITY.md)。
- 坐席通话状态只由 features/call/stores/useCallStore 持有；旧 stores/callStore 是兼容导出，不形成第二份状态。小结 REST 是独立适配器。
- agentEventRouter 负责业务 WS 消息映射，不制造最终成功；命令应答仍区别于话务结果。useAgentRuntime 管理 WS/SIP 订阅、异步初始化代次和退出清理。
- SIP 订阅提供所有者取消函数，旧订阅释放不能清除新订阅；通话计时/响铃跟随 store 生命周期，管理端短时提示计时器跟随组件作用域。

## 3. 自动检查入口

后端目录执行：

```bash
node tools/check-engineering-governance.mjs
node --test tools/check-engineering-governance.test.mjs
node --test tools/check-system-model-data.test.mjs
node --check tools/check-engineering-governance.mjs
mvn -q -DskipTests compile
mvn -q test
```

两个前端均执行 `npm run check`，顺序为治理检查、单元测试、类型/生产构建。Electron 另外执行 `npm run desktop:test`。

治理脚本要求后端与两个前端按当前同级目录布局存在，扫描 common/server/admin/client/console 和 SDK starter 的 Java、TS、Vue 源码。当前覆盖 486 个生产文件，并检查五个后端模块的 POM 产品依赖。SDK 尚无源码时允许空目录，但模块 POM 缺失仍会失败。规则包括：默认 Java 800 行、Vue/TS 600 行预算，页面壳不导入传输适配器，已治理管理页不直接导入 API，禁止后端实现跨模块依赖、SDK 运行入口以及部分 Long ID 数值转换模式。POM 检查限于显式声明的产品依赖，不解析 Maven 继承或外部依赖树；它是增量静态门禁，不替代完整架构、授权或协议审查。

已评审但暂不继续拆分的文件采用冻结预算：GroupManageView 869；CdrReportView 841；OrgReportView 768；AgentManageView 728；useGroupManagement 723；IvrFlowView 653。预算必须随责任评审更新，不能通过提高阈值绕过门禁。FlowStudio 已拆出执行轨迹查询、响应装配、发布校验和运行通知，主服务降至 569 行并移除超长豁免；草稿与发布写入仍由主服务编排；剩余较大页面主要是展示/表单结构，下一轮按可独立验证的子视图治理。

## 4. 当次验证

- JDK 21 编译通过。隔离选择的后端单元测试 163 项通过（common 28、server 99、admin 36）；显式加载本机 ByteBuddy agent 解决沙箱 Mockito 自附加限制，未更改依赖。
- 原 SystemFlowModelsTest 的 PLAY_ONLY 不可达问题已通过模型分支修正解决，未删除测试或降低可达性断言；所有 common 测试通过。默认全量 Maven 含真实服务及会清理业务表的集成测试，必须在专用隔离环境执行，本次不宣称全量集成绿色。
- 真实数据库/话务集成测试没有执行。本轮重新生成全新数据库的模型基线并提供存量目录 V2 升级脚本；SQL 仅完成静态审查、唯一发布约束和资源校验和检查，未在 MySQL 8 执行。不改表结构或现有通话快照。
- 前端单元测试：坐席端 41 项、管理端 20 项，本次均复验且生产构建通过。Electron 独立测试 5 项（此前治理轮验证）。治理及模型数据脚本合计 7 项测试通过。
- 浏览器使用临时模拟适配器，不连接实际数据库：检查空列表、零值报表、请求失败、未授权提示、旅程页面及 390px 窄屏坐席表单。测试夹具随后移除。未验证真实登录跳转、CDR 全部交互、媒体权限或话务端到端。
- 真实 FreeSWITCH、NATS、SIP/WebRTC 双向媒体、多节点恢复、Windows 安装包与运营商线路均未在本轮验收。构建存在既有大 chunk 警告，未宣称性能优化完成。

## 5. 分开跟踪的缺陷与后续工作

以下不属于本轮结构治理完成项：

1. 监控页已在后续可靠性整改中切换服务端完整周期聚合；组织等其他报表的大页读取仍不能保证完整聚合，索引和真实统计口径尚待验收。当前状态见 [运行可靠性整改](RUNTIME-RELIABILITY.md)。
2. 原有远程调用事务边界、组删除 SQL 循环及持久转接恢复需要独立设计与测试；拆分服务不等于这些问题消失。
3. 坐席参与、多 Leg 释放、READY 与结束通知竞态和日终修复仍按 [通话生命周期决策](CALL-LIFECYCLE-DECISION.md) 验收；已明确不强制 ACW、立即恢复原示闲/示忙，不在工程治理文档重复定义策略。
4. 为冻结大文件安排责任明确的小批次治理，并补认证浏览器、MySQL 8 和话务链路验收证据。

已修复的展示问题：无执行记录显示明确空态；旅程状态、时间、耗时来自执行证据，命令完成不被解释为接听或录音成功；失败桥接的别名卡片可定位原始尝试。监控区分 loading/empty/error/retry，失败时隐藏报表，旧请求和组件销毁后的结果不能更新当前页面。切换通话同时清空旧版本和流程状态。上述行为通过纯函数/请求生命周期单元测试，以及桌面和 390px 窄屏的模拟浏览器验证；不是实际话务验收。

本轮未提交、未暂存；保留工作区既有业务优化与其他产品变更。

## 6. 后续业务规则修正

在结构治理之后，按用户决定实现立即恢复原示闲/示忙：移除 ACW 超时调度与旧小结置闲 SQL，释放入口增加共享活跃坐席 Leg 检查；源结束处理增加 Call 行锁。客户端小结与媒体占用分离，旧结束通知、旧小结回包和退出登录后的回包不能清理新通话。这属于独立业务规则修正，不是纯结构拆分。桌面与 390px 模拟浏览器检查了结束后 IDLE、下一通自动收起抽屉、保存旧小结不清除新通话及示忙下的小结布局；没有真实话务验收。新增多 Leg/未知归属 SQL 集成回归夹具，但未在 MySQL 8 执行。

## 7. 第二轮结构治理

- `FlowStudioService` 从 887 行降至 813 行；`application/query/FlowExecutionQueryService` 为 91 行，拥有执行轨迹权限校验、通话存在性检查、游标查询和响应装配。Controller 分派到查询服务，原维护服务继续拥有草稿、发布与路由目标校验，不新增水平 service 包。
- `GET /api/admin/flow-studio/calls/{callId}` 的路径、DTO、`flow:view` 权限、100 条游标分页和 Mapper SQL 不变；管理端 `flowApi.execution` 及其字符串 ID 契约不变。没有 schema、Sidecar 协议、依赖或话务状态变更。
- 新增五项查询边界测试：授权拒绝、非法身份、通话不存在、空轨迹、大整数 ID 与满页游标/事实字段透传。治理脚本新增 common/SDK 源码和 POM 边界回归；冻结预算只下降，不增加豁免。
- 本轮实际修改范围为两个生产 Java 文件、一个新增查询服务、一个新增 Java 测试、治理脚本与测试、本文档；两个前端仅复验，没有新增界面修改。本轮没有重新做浏览器和真实服务联调，不把前一轮浏览器证据算成本轮端到端通过。保留未提交的既有业务优化和其他产品改动。

## 8. Flow Studio 职责继续收敛

在第二轮查询拆分后继续治理：主服务由 813 行降至 569 行，相比最初 887 行减少 318 行（约 36%），恢复默认 Java 800 行门禁，不保留超长豁免。新职责分别为 `FlowResponseAssembler` 66 行、`FlowPublicationPolicy` 130 行、`FlowRuntimeNotifier` 98 行；执行轨迹查询仍为 91 行。整体代码未刻意压缩，增加的是明确边界和可独立验证的行为。

主服务保留草稿及发布事务、权限检查和 after-commit 触发点；校验对象负责 DID 入口及去重后的批量路由目标查询，装配对象负责公开版本格式与按需定义详情，通知对象负责配置、惰性 HTTP 客户端、Redis 广播和异步重载。原 `PENDING` 激活状态、重载 URL/鉴权头/1500ms 超时和通知失败不回滚发布事实的行为不变。没有新增 Outbox、通知重试或持久激活确认能力，不将结构治理等同可靠投递能力。

新增九项装配、校验、通知测试，既有创建/发布测试改为组合真实拆分对象，继续验证提交前不通知、提交后才通知以及入口/路由拒绝。JDK 21 编译、163 项隔离单元测试、七项治理/模型脚本测试通过；静态门禁扫描 486 个生产文件无发现。此次无前端、DDL、Mapper SQL 或协议修改；没有新增浏览器、真实数据库、Redis 或运行端重载联调证据。未暂存或提交，保留无关工作区变更。
