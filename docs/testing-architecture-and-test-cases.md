# FCC 测试策略与用例

## 1. 目的

本文规定 FCC 变更的最低验证层级、核心场景和结果记录方式。它不保存某一次执行的测试数量或机器故障。测试文件存在、Mock 通过、页面可打开或 RPC 返回 `ACCEPTED` 都不能替代真实话务验收。

验证结论必须区分：

1. 静态检查或构建通过；
2. 自动化测试通过；
3. 依赖真实基础设施的集成测试通过；
4. FreeSWITCH/SIP/双向媒体/Windows 人工验收通过；
5. 未执行或失败，并说明原因。

## 2. 分层测试矩阵

| 变更类型 | 最低自动化 | 必要环境验证 |
| --- | --- | --- |
| Java DTO、枚举、序列化、工具 | 相关单元测试；全项目 compile/test | 对外 JSON 变化需真实 HTTP 契约检查 |
| Controller、Application、Service、Mapper | compile/test；Mapper XML 解析；权限和事务否定用例 | MySQL/Redis 下验证锁、唯一约束、分页和回滚 |
| Flow Model 与 Action | 资源解析；动作目录完备性；定义校验；版本并发测试 | admin 保存/发布、server 加载固定版本和真实通话轨迹 |
| NATS/FNode 命令 | Java/Go fixture；非法值、超时、重复和未知结果测试 | NATS + Sidecar + FreeSWITCH，核对命令与最终事件 |
| 事件与状态机 | 重复、乱序、晚到、终态幂等、重启恢复测试 | JetStream 重投、Sidecar outbox、FreeSWITCH 快照竞态 |
| SIP/WebRTC | Session/Call 关联、资源清理和失败状态测试 | 真实 WSS、麦克风、ICE/TURN、双向音频和双方挂机 |
| 业务 WebSocket | 握手鉴权、心跳、断线、重连、重复 Session 测试 | 浏览器/Electron 登录、网络切换和服务重启 |
| 管理端 CRUD | API adapter、字符串 ID、分页、空/错/无权限状态 | 认证浏览器操作、长文本和移动敏感宽度 |
| Windows 客户端 | IPC 白名单、通知去重、回执和退出清理测试 | 最小化、托盘、锁屏、专注助手、重连和安装包 |
| Sidecar 运维 | 参数校验、凭据脱敏、stale/错误映射测试 | PostgreSQL、ESL、注册终端；破坏性操作使用隔离环境 |
| Schema | DDL 静态审查、实体/Mapper 对齐 | 一次性 MySQL 8/PostgreSQL 空库执行；生产期再验证迁移和回退 |

## 3. 核心业务用例

### 3.1 身份、终端和权限

- 首个管理员 bootstrap 默认关闭；缺少配置时拒绝；已有控制台账号时不覆盖。
- 坐席只能取得本人 SIP 配置；响应不可缓存，日志和前端存储没有明文口令。
- `WEBRTC -> SIP -> WEBRTC` 切换始终只有一个活跃终端；通话中和 ACW 期间拒绝切换。
- 物理话机 `0000` 绑定覆盖成功、错误工号、未知分机、冲突分机、重复 DTMF 和并发换绑。
- 未登录、停用账号、伪造工号、跨坐席控制、客户/录音越权全部被后端拒绝。

### 3.2 呼入、外呼和事件

- DID 精确命中发布流程；不存在、停用或未绑定 DID 时明确失败。
- 菜单按键、if 分支、唯一 else、收号超时、无坐席、排队超时和客户提前挂机。
- 系统先呼坐席：坐席拒接或超时不得继续呼客户；客户忙线/超时有明确终态。
- 坐席终端主动拨号：接管认证坐席 Leg，不重复呼叫坐席；未绑定终端拒绝。
- Channel 正常链路及重复、乱序、未知 Channel、单边挂机、双方重复挂机。
- 命令超时或应答未知时先查询/对账，不自动重复产生副作用。
- 服务重启后从 MySQL、Sidecar 快照和事件事实恢复，或明确进入 `UNKNOWN` 待核对。

### 3.3 流程、录音与业务闭环

- 流程编码唯一；首个草稿由服务端分配版本；已发布版本只读。
- 并发发布最多一个版本生效；发布失败保留旧版本；存量通话不切换版本。
- `BRANCH.defaultRoute` 与 `ROUTE` 编辑同一份 else 数据，重复按键和未知字段被拒绝。
- 五个固定系统模型都可加载，所有 FNode 方法都有公共 Action 对应关系。
- 录音开始/停止、重复事件、文件缺失、完成态、范围读取和下载审计。
- 坐席先挂机进入评价/结束语音；客户先挂机不继续播放；评分与超时路径均留痕。
- 第三方动作拒绝模型中的任意 URL，校验协议版本、`commandId` 和结构化响应。

### 3.4 自动外呼、回拨和弹屏

- 通知型的应答、按 1 确认、未确认、忙线、无应答和可重试分类。
- 渐进式先预占/呼叫坐席，再呼客户；坐席忙碌不消耗客户拨号尝试。
- 领取租约、多实例竞争、暂停、取消、重启恢复、结果未知和成功项不重拨。
- 回拨原 Call 关联、原子领取、重复点击和并发状态变化。
- 弹屏收件人、过期、断线补发、重复回执、通话结束撤销和客户详情授权。

### 3.5 前端和运维

- 列表分页、空数据、失败重试、登录失效、无权限、stale 数据和长文本。
- Java `Long`/雪花 ID 全程作为字符串；禁止 `Number`、`parseInt`、算术或数值排序。
- SIP 注册失败、麦克风拒绝、ICE 失败、第二来电、远端挂机和旧 Session 晚到。
- 页面卸载、退出、终端切换和重连不遗留定时器、监听器、UA、Session 或 MediaStream。
- Sidecar、PostgreSQL、ESL 分别不可用时不展示伪造的健康、容量、网关状态或 RTT。
- 强拆、注销、转接、网关修改和 NativeAPI 有确认、授权、审计和失败反馈。

## 4. 标准命令

JDK 21 FCC：

```bash
mvn -q -DskipTests compile
mvn -q test
```

Go Sidecar：

```bash
go build -o fs-sidecar-agent .
go test ./api ./config ./db ./esl ./event ./governance ./nats ./rpc
```

`test/nats_client_demo.go` 与 `test/verify_fnode_flow.go` 是两个独立手工联调程序，不计入自动化通过率，也不能用 `go test ./...` 一并编译：

```bash
go run test/nats_client_demo.go
go run test/verify_fnode_flow.go
```

管理端：

```bash
npm ci
node --test tests/governance.test.mjs
npm run build
```

坐席端：

```bash
npm ci
npm run test
npm run desktop:test
npm run build
```

运维端：

```bash
npm ci
npm run build
```

所有工程完成后执行 `git diff --check`。变更 JavaScript 模块时额外执行 `node --check <file>`；变更 Mapper XML 时解析所有受影响 XML；视觉变更必须在浏览器检查桌面、长文本和移动敏感宽度。

## 5. 结果记录模板

```text
变更范围：
环境与外部依赖：
已通过：
失败（首个有效错误）：
未执行及原因：
真实 FreeSWITCH/SIP/媒体/Windows 验收：
剩余风险：
```

交付说明不得把缺少外部依赖写成通过，也不得用 Mock、静态页面、日志中“已发送”或脚本文件存在代替端到端证据。
