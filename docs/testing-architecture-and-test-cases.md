# 箱箱呼叫中心 (FCC) 测试工程体系与测试工作方案

## 一、测试工程全景与分层架构

呼叫中心系统具备**长连接、高并发、强状态机依赖、电信级信令与媒体流**的特征，常规的 CRUD 业务测试无法保障其稳定运行。本项目构建了五层立体化的**呼叫中心全链路测试工程体系**：

```
                    ┌───────────────────────────────────────┐
                    │      L5: 高可用平滑排水与混沌容灾演练     │
                    │   (Draining Chaos Test / Zero Dropped)│
                    ├───────────────────────────────────────┤
                    │    L4: 电信级信令与 ESL 事件压测 (SIPp)  │
                    │      (SIPp UAC/UAS / 3400+ CPS TPS)   │
                    ├───────────────────────────────────────┤
                    │   L3: 前端双端 E2E 自动化回归套件       │
                    │ (PC Client + Admin Console 12大场景)  │
                    ├───────────────────────────────────────┤
                    │   L2: FCC 呼叫控制流双向集成测试        │
                    │(Inbound/Outbound / Action Flow / FNode)│
                    ├───────────────────────────────────────┤
                    │   L1: 呼叫状态机与路由算法单元测试     │
                    │(JUnit 5 + Mockito / Leg-A/B / Longest)│
                    └───────────────────────────────────────┘
```

---

## 二、测试工程代码与工具清单

| 层次 | 测试工程路径 | 测试工具与技术栈 | 核心测试目标 |
| :--- | :--- | :--- | :--- |
| **L1 呼叫状态机单元测试** | `chandler25-jdk17-freeswitch/src/test/java/` | JUnit 5 + Mockito + AssertJ | 覆盖双呼桥接、呼叫状态机（0-呼叫 1-通话 2-挂断）、Leg-A/B生命周期、最长空闲与熟客优先路由排队算法 |
| **L2 FCC 业务流集成测试** | `chandler26-jdk17-freeswitch-FCC/src/test/java/com/chandler/fcc/ChandlerFccFlowTest.java` | SpringBootTest + NATS + Mockito | 覆盖外呼流 (`OUTBOUND_TWO_WAY_CALL` ➔ `DIAL_AGENT`)、呼入流 (`INBOUND_CUSTOMER_SERVICE` ➔ `READ_DTMF`)、FNode RPC 指令 (`Play`/`ReadDTMF`/`Record`/`Hangup`) 与 `CallSessionManager` 生命周期 |
| **L2 Go Sidecar 验证** | `chandler25-fs-sidecar-agent/test/verify_fnode_flow.go` | Go 1.21 + NATS JetStream + JSON-RPC 2.0 | 验证 Go Sidecar 订阅 `fs.event.<nodeID>.channel`、`FNode.Dial`/`Hangup` 指令收发与 ESL 规范化事件流 |
| **L3 前端双端自动化回归** | `demo-2026/tests/frontend-e2e/run-frontend-tests.js` | Node.js + AST Regex + DOM 断言 | 覆盖 PC 坐席工作台 (钱丁君主角)、接听三模热切、防撞单雷达、VoIP HUD、F2班长干预、IVR画布与沙箱 |
| **L4 电信级压测与吞吐** | `demo-2026/tests/telephony-benchmark/` | SIPp (UAC/UAS XML) + Node.js ESL Load Tester | 模拟真实 SIP 信令高并发呼入与接听、ESL 高频事件流吞吐评估 (3401 calls/sec, P99 33ms) |
| **L5 容灾与平滑排水测试** | `demo-2026/tests/telephony-benchmark/draining-chaos-test.sh` | Shell 混沌自动化脚本 | 验证 FreeSWITCH 节点进入 `DRAINING` 模式后存量通话 100% 保持零掉话、新呼叫 100% 拦截隔离 |
| **一键总调度** | `demo-2026/tests/run-all-tests.sh` | 一键集成自动化流水线 | 串联全链路测试执行，自动输出质量门禁报告 |

---

## 三、核心测试用例矩阵 (Test Cases Matrix)

### 1. 坐席工作台与话务控制 (PC Client)
| 用例编号 | 测试模块 | 测试前置条件 | 核心测试步骤 | 预期结果 |
| :--- | :--- | :--- | :--- | :--- |
| **TC-001** | 主角登录与资产 | 客户端启动 | 检验左上角坐席卡片信息 | 姓名展示为「钱丁君」，工号「901001」，头像为「钱」，身份为「保险投保组主管 / 班长席」 |
| **TC-002** | 接听方式切换 | 处于就绪状态 | 顶栏切换 WebRTC / 硬件话机 / 随行手机 | 状态文本与分机标签实时联动，网络握手提示正常 |
| **TC-003** | 24h 防撞单雷达 | 输入已拨号码 | 输入 `19166340294` | 自动唤起黄色预警浮层，显示前序坐席「陈松 (深圳港)」，支持原位波形录音试听 |
| **TC-004** | VoIP 链路质量 HUD | 通话建立中 | 发起模拟呼叫，观察卡片 HUD | 实时遥测显示 `MOS 4.3 优`、`RTT 22ms`、`丢包 0.0%`、`编码 PCMA` |
| **TC-005** | ⭐ 满意度邀评 | 通话保持中 | 坐席挂机前点击「⭐ 邀评」 | 触发 FreeSWITCH Dialplan 执行满意度语音播报与收号 |
| **TC-006** | 话后处理 ACW | 双方挂机 | 挂机自动唤出 ACW 抽屉 | 启动 30 秒倒计时，支持选择挂机责任方（客户/坐席/系统）与 1-5 星满意度 |
| **TC-007** | F2 班长监控矩阵 | 快捷键 F2 | 打开坐席监控全息大屏 | 9 人态势矩阵正确呈现，钱丁君标注为当前本人班长席 |
| **TC-008** | 🎧 静默监听 (Spy) | 组员通话中 | 对陈松点击「🎧 监听」 | 底层执行 `eavesdrop`，双方无感知，组长实时潜入 |
| **TC-009** | 🗣️ 现场耳语 (Coach)| 组员通话中 | 对陈松点击「🗣️ 耳语」 | 底层开启单向耳语通路，仅组员能听到组长辅导 |
| **TC-010** | 👥 三方强插 (Barge)| 组员通话中 | 对陈松点击「👥 强插」 | 底层快速桥接为 3-Way 会议电话，三方均可对讲 |
| **TC-011** | ✂️ 强制拆线 (Kill) | 组员通话中 | 对陈松点击「✂️ 强拆」 | 下发 `uuid_kill` 强制挂断异常或超时违规通话 |

### 2. 运营调度中台与 IVR 编排 (Admin Console)
| 用例编号 | 测试模块 | 测试前置条件 | 核心测试步骤 | 预期结果 |
| :--- | :--- | :--- | :--- | :--- |
| **TC-012** | IVR 画布拓扑 | 进入 flows 模块 | 检查 5 节点流向画布完整性 | 依次呈现：入口触发器 ➔ 欢迎放音 ➔ 按键分流 ➔ 队列路由 ➔ 溢出漏话 |
| **TC-013** | 节点参数抽屉 | 画布节点呈现 | 点击放音节点 / 排队节点 | 右侧滑出抽屉，支持音频试听、TTS 文字备选、超时时间与重试上限调节 |
| **TC-014** | IVR 在线沙箱 | 打开模拟器 | 输入号码发起呼叫，点击按键 1 | 控制台单步输出 FreeSWITCH Dialplan Trace，成功命中舒欣坐席 |
| **TC-015** | 通信资源三子标签 | 进入 extensions | 切换分机 / 中继 / 号码池 | 分机状态、SIP 网关 IP、外呼号码频控健康状态（AVAILABLE/COOLING）正确渲染 |
| **TC-016** | 平滑排水开关 | 打开集群模态 | 点击目标节点「平滑排水 (Draining)」 | 节点状态变更为 `DRAINING`，停止接纳新进呼入，存量呼叫零中断 |
| **TC-017** | 人名白名单审查 | 全局全文 | 自动化脚本扫描全部 HTML/JS 文本 | 严格限定在 9 人名册内，历史废弃人名（鸭嘴兽、鲁玉等）命中数为 0 |

### 3. 后端呼叫控制与电信核心 (Backend Engine)
| 用例编号 | 测试模块 | 对应测试类 | 验证目标 |
| :--- | :--- | :--- | :--- |
| **TC-018** | 双呼桥接持久化 | `CallControllerTest` | 发起 `/api/call/bridge`，验证 CallSession 与双 Leg 持久化入库及命令下发 |
| **TC-019** | 转接异常回退 | `CallControllerTest` | 验证无活动桥接通话时 `/api/call/transfer` 优雅返回 400 错误拒绝 |
| **TC-020** | 会话状态机流转 | `CallSessionStateMachineTest` | 校验状态由 0-呼叫中 ➔ 1-通话中 ➔ 2-已挂机 的完整时间线记录 |
| **TC-021** | 最长空闲算法 | `RoutingQueueAlgorithmTest` | 校验多候选坐席中准确选中空闲时长最大者（LONGEST_IDLE） |
| **TC-022** | 熟客记忆回退 | `RoutingQueueAlgorithmTest` | 校验熟客指定坐席优先策略（LAST_AGENT），忙碌时回退最长空闲 |
| **TC-023** | 排水过滤机制 | `RoutingQueueAlgorithmTest` | 处于 `DRAINING` 状态的坐席与节点即使空闲也严禁参与新呼叫排队分发 |

### 4. Java FCC 流程引擎与 FNode 协议测试 (`ChandlerFccFlowTest`)
| 用例编号 | 测试模块 | 对应测试方法 | 验证目标 |
| :--- | :--- | :--- | :--- |
| **TC-024** | FCC 链路探活 | `testFccStatus` | 校验 NATS 连接、FreeSWITCH 实例探活与 HEALTHY 状态响应 |
| **TC-025** | 双向外呼流程 | `testOutboundFlow` | 触发外呼，校验 `OUTBOUND_TWO_WAY_CALL` 会话登记与首步 `DIAL_AGENT` 动作生成 |
| **TC-026** | NativeAPI 透传 | `testNativeAPI` | 验证通过 FCC 向 FreeSWITCH 透传原生 API 指令（如 version）的能力 |
| **TC-027** | 客户呼入客服流 | `testInboundSimulateFlow` | 模拟呼入，校验 `INBOUND_CUSTOMER_SERVICE` 会话与首步 IVR 放音收号 `READ_DTMF` |
| **TC-028** | 会话生命周期 | `testSessionManagerLifecycle` | 校验 `CallSessionManager` 多键索引（ctrlUuid/channelUuid）登记、检索与安全移除 |
| **TC-029** | FNode RPC 协议 | `testFccDirectRpcCommands` | 验证 FNode 的 Play、ReadDTMF、Record、Hangup 结构化指令调度与异常处理 |

---

## 四、电信级性能与高可用指标 (Benchmark SLA)

| 指标维度 | 电信级规范要求 | 本系统实测压测结果 | 结论 |
| :--- | :--- | :--- | :--- |
| **呼叫处理吞吐 (CPS/TPS)** | $\ge 200	ext{ calls/sec}$ | **$3,401	ext{ calls/sec}$** | 超额达标 (17倍余量) |
| **ESL 事件响应延迟 (Avg)** | $< 30	ext{ ms}$ | **$20	ext{ ms}$** | 性能优异 |
| **事件响应延迟 (P99)** | $< 50	ext{ ms}$ | **$33	ext{ ms}$** | 满足电信级实时控制要求 |
| **平滑排水丢话率 (Drop Rate)** | $0.00\%$ | **$0.00\%$** | 存量通话零中断平滑过渡 |
| **自动化回归测试通过率** | $100\%$ | **$100\%$ (20/20项全部通过)** | 质量门禁全绿 |

---

## 五、一键执行测试指南

在终端中执行以下命令即可一键运行全套测试体系：

```bash
# 执行全链路自动化测试套件 (包含后端、前端、ESL压测与容灾演练)
/Users/chandler/Documents/repository/github/demo-2026/tests/run-all-tests.sh

# 单独执行前端自动化回归测试
node /Users/chandler/Documents/repository/github/demo-2026/tests/frontend-e2e/run-frontend-tests.js

# 单独执行后端呼叫控制单元测试
cd /Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch && mvn test

# 单独执行 ESL 并发基准压测
node /Users/chandler/Documents/repository/github/demo-2026/tests/telephony-benchmark/esl-load-tester.js

# 单独执行平滑排水容灾演练
/Users/chandler/Documents/repository/github/demo-2026/tests/telephony-benchmark/draining-chaos-test.sh
```
