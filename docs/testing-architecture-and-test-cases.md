# FCC 测试现状与交付验证要求

## 1. 文档目的

本文记录仓库中实际存在的测试资产和 FCC 变更的最低验证要求。测试文件存在不等于已经在当前环境通过；依赖 MySQL、Redis、NATS、Sidecar、FreeSWITCH、SIP 终端或浏览器的结果必须单独说明。

## 2. 当前自动化资产

### 2.1 JDK 21 FCC

`chandler26-jdk21-fcc` 当前包含：

- `fcc-common`
  - `IdUtilTest`
  - `TimeUtilTest`
  - `FccIdentifierJacksonModuleTest`
  - `FccEventMethodsTest`
- `fcc-server-starter`
  - `AgentWebSocketIntegrationTest`
  - `DatabaseConnectionTest`
  - `FccTelephonyFlowTest`
  - `FlowActionIdentityTest`
- `fcc-admin-starter`
  - `AgentAccountServiceTest`
  - `CallCdrAndResourceAdminTest`
  - `ExtensionAdminIntegrationTest`
  - `P0P1CoreFeaturesTest`

这些测试覆盖部分工具、坐席 WebSocket、数据库连接、话务流程、坐席账户、资源、CDR、回拨、分机和流程定义。环境型测试是否可运行取决于本地依赖与测试配置。

### 2.2 JDK 17 FCC 验证工程

`cloud-2025/chandler26-jdk17-freeswitch-FCC` 包含一个 `ChandlerFccFlowTest`，用于验证前代内存流程实现、FNode RPC 和 Sidecar 分机管理。它不是当前管理端/坐席端的主后端测试套件。

### 2.3 Go Sidecar

Sidecar 已有 `event/normalizer_test.go`、`rpc/handler_test.go`、`db/models_test.go`，分别检查录音事件契约、规范方法/旧别名拒绝、凭据和路径序列化边界。仓库中的：

- `test/nats_client_demo.go`
- `test/verify_fnode_flow.go`

是手动联调程序，不应计入 `go test` 覆盖率或自动化通过率。

两个程序位于同一目录并分别定义了 `main` 和同名 DTO，因此当前执行 `go test ./...` 会在工具链编译阶段产生包内重复定义。自动化命令需要显式列出 Sidecar 生产包，手工程序分别使用 `go run <file>`。

### 2.4 前端

当前测试资产：

- 管理端 `tests/governance.test.mjs`：4 个模型测试，覆盖定义 JSON、组树与字符串 ID、CDR 缺失/零测量值。
- 坐席端：4 个测试文件、10 个测试，覆盖运行配置、呼叫状态、控制结果、DTMF 单路径和 WebSocket 认证头。
- `cloud-2025/fswitch-web`：尚无自动化测试；本轮执行构建、额外类型检查及离线浏览器检查。

`npm run build` 只能证明类型检查/打包链路，不证明 API、呼叫状态机、权限或浏览器交互正确。

### 2.5 Shell 验证脚本

仓库存在：

- `tests/run-all-tests.sh`
- `tests/telephony-benchmark/draining-chaos-test.sh`

脚本存在不代表已经在当前拓扑执行或达到其目标。运行前必须检查路径、端口、依赖和脚本假设，运行后保留命令、环境与结果。

## 3. 分层验证矩阵

| 变更类型 | 最低自动化 | 环境验证 |
| --- | --- | --- |
| Java DTO/枚举/工具 | 相关单元测试、全量 compile/test | 无 |
| Java Controller/Application/Mapper | compile/test、Mapper XML 解析、契约测试 | 可用 MySQL/Redis 下验证事务和查询 |
| NATS/FCC 命令 | 协议 fixture、超时/无响应/重复测试 | NATS + Sidecar 联调 |
| 事件与呼叫状态机 | 正常、重复、乱序、终态幂等、重启恢复测试 | Sidecar + FreeSWITCH 事件闭环 |
| WebSocket | 握手、心跳、断线、重连、重复会话与清理测试 | 浏览器或 WebSocket 客户端 |
| SIP/WebRTC | 状态与资源清理单测 | 真实浏览器、麦克风、SIP WS/WSS、双向音频 |
| 管理端 CRUD | API adapter/权限/分页/错误状态测试 | 浏览器 + fcc-admin |
| 运维控制 | 命令校验、错误映射、stale 状态测试 | Sidecar + FreeSWITCH；破坏性操作使用隔离环境 |
| 数据库 DDL | 静态审查、迁移顺序、Mapper 对齐 | 一次性 MySQL 8 实例执行与回滚/修复演练 |

## 4. 必测业务场景

### 4.1 呼叫与事件

- 外呼命令受理、超时、无响应和 Sidecar 业务错误。
- Channel START/RINGING/READY/BRIDGE/DESTROY 正常链路。
- 重复事件、乱序事件和未知 Channel。
- 坐席/客户单边挂机、双方重复挂机和满意度分支。
- 转接过程中原坐席退出、目标坐席失败和客户提前挂机。
- 录音开始、停止、文件缺失和元数据重复上报。
- 使用协议 fixture 校验 Sidecar 和 Java 对录音方法名、字段名及 NATS category 完全一致；规范方法为 `Event.Recording`，category 为 `record`，不接受旧别名。
- 服务重启后从 MySQL/Sidecar 恢复或明确进入待核对状态。

### 4.2 坐席端

- 登录过期和无权限。
- 业务 WebSocket 心跳超时、断线重连和登出清理。
- SIP 注册成功、失败、麦克风拒绝和媒体建立失败。
- WebSocket 与 SIP 对同一接听/挂机事件重复通知。
- 页面卸载、终端切换和重新登录不遗留定时器或音频 Session。
- ACW 只进入一次，并在提交/取消后恢复一致状态。

### 4.3 管理端

- 列表分页、空数据、筛选保持、详情加载失败。
- 对大于 JavaScript 安全整数范围的 `Long` ID 做序列化与前端透传测试。
- 坐席/组/分机变更的后端授权和操作审计。
- 流程草稿保存、发布失败、运行时 reload 未确认和版本回看。
- 录音无权限、文件不存在、范围请求和下载审计。
- 回拨任务重复指派、重复发起和并发状态变化。

### 4.4 运维端

- Sidecar、PostgreSQL 或 ESL 分别不可用。
- 上次数据 stale 标识和恢复刷新。
- 强制注销、强拆、转接、网关修改、reload 和原生命令的确认与失败反馈。
- 日志 WebSocket 断线重连、消息突发和页面卸载清理。

## 5. 标准命令

JDK 21 FCC：

```bash
mvn -q -DskipTests compile
mvn -q test
```

JDK 17 FCC：

```bash
mvn -q -DskipTests compile
mvn -q test
```

Go Sidecar：

```bash
go build -o fs-sidecar-agent .
go test ./api ./config ./db ./esl ./event ./governance ./nats ./rpc
go run test/nats_client_demo.go
go run test/verify_fnode_flow.go
```

三个前端分别执行：

```bash
npm ci
npm run build
```

变更完成后：

```bash
git diff --check
```

## 6. 结果记录

2026-09-19：使用临时 JDK 21 后 Java compile 和 15 个定向测试通过；完整 `mvn -q test` 因本地 NATS 不可连接导致集成测试失败。两端新构建、管理端 4 个模型测试、坐席端 10 个测试通过。定向 Java 命令、部署顺序和新增测试详见 [契约修复记录](fcc-contract-remediation.md)。此前运维端构建通过；Go 工具链限制仍存在。真实数据库、NATS、ESL、SIP、媒体及登录态浏览器回归尚未完成。

交付说明必须区分：

- 已执行且通过；
- 已执行但失败，并给出首个有效错误；
- 因缺少外部依赖未执行；
- 仅完成静态检查；
- 浏览器、媒体或真实电话链路未验证。

禁止用 Mock、静态页面、命令受理响应或脚本文件存在来替代端到端通过结论。
