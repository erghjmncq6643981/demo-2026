# FCC 跨电脑部署与验收

## 1. 文档目的

本文用于部署当前 FCC 源码并完成真实环境验收，不是“已经验收通过”的证明。测试结果应记录在当次交付或验收报告中，不回填本文。

至少准备 MySQL 8、Redis、启用 JetStream 的 NATS、PostgreSQL、Go Sidecar、FreeSWITCH、一个 SIP 终端，以及可运行浏览器/Electron 的 Windows 电脑。涉及 WebRTC 时还需可用的 WSS、麦克风、DTLS-SRTP 与必要的 ICE/TURN 配置。

## 2. 部署顺序

1. **初始化业务库**：创建空 MySQL 8 数据库，使用 `utf8mb4` 执行 `chandler26-jdk21-fcc/docs/fcc-schema.sql`。不得导入旧呼叫中心表、话单、流程 JSON 或演示账户。
2. **准备基础设施**：启动 Redis、PostgreSQL 和 NATS/JetStream。使用 `chandler26-jdk21-fcc/docs/fcc-events-stream.json` 创建或核对 `FCC_EVENTS`；生产副本数、容量和保留期按实际拓扑调整。
3. **启动 Sidecar**：每个进程配置唯一 `NODE_ID` 和独占持久目录 `COMMAND_JOURNAL_DIR`、`EVENT_OUTBOX_DIR`，再配置 ESL、NATS、PostgreSQL、TTS 及媒体共享目录。目录不得位于临时盘，也不得由多个进程共享。
4. **验证 FreeSWITCH 入口**：配置真实 SIP Profile、运营商 context、DID、`default` 内部分机 context、录音/TTS 共享目录；将 `cloud-2025/chandler25-fs-sidecar-agent/examples/freeswitch/dialplan/default/10_fcc_phone_binding.xml` 部署到 FreeSWITCH `dialplan/default` include 目录并执行 `reloadxml`。确认 Sidecar 达到可接单状态后再启动 Java。
5. **启动 Java 服务**：配置 MySQL、Redis、`NATS_URL`、`FCC_ADMIN_BASE_URL`、`FCC_SERVER_BASE_URL`、允许的 WebSocket Origin 和录音共享根目录。Java 不配置默认 `nodeId`，业务命令统一发送到 `fs.cmd.dispatch`。
6. **创建首个管理员**：首次启动 `fcc-admin` 时显式设置 `FCC_BOOTSTRAP_ADMIN_ENABLED=true` 及操作者选择的用户名、显示名和 8–64 位密码。成功登录后立即移除全部 `FCC_BOOTSTRAP_ADMIN_*` 变量并重启；日志和验收材料不得记录密码。
7. **配置业务数据**：创建坐席、组、分机、终端、DID、外呼号码/context，创建并发布流程。确认每个呼入流程至少绑定一个启用 DID。
8. **部署前端**：分别路由 `/api/admin`、`/api/telephony`、`/ws/agent` 和 Sidecar 运维入口；WebSocket 代理必须支持 Upgrade。业务工作台和运维台保持独立访问控制。
9. **部署 Windows 测试客户端**：配置可信 HTTPS 工作台地址。未签名安装包只能用于受控测试，不能描述为正式可信发布。

当前多节点 Coordinator 的 ownership、跨节点 Bridge 和聚合快照尚未完成真实验收。在完成前只部署一个 dispatch ingress 和一个活跃 `fcc-server`，不要让多个 Sidecar 同时竞争 `fs.cmd.dispatch`。

## 3. 配置检查

### 3.1 标识和路由

- `callId` 是无前缀正整数雪花 ID，HTTP/WS 以字符串传递；不兼容 `call-` 前缀或哈希转换。
- `channelUuid` 是标准 FreeSWITCH UUID，不能代替 `callId`。
- Java 命令使用业务号码与 `routingContext`；gateway、SIP Profile 和线路映射留在 Sidecar/FreeSWITCH。
- `nodeId` 只从命令应答、事件或快照返回，不作为拨号请求参数。

### 3.2 话机与媒体

- 已认证物理话机拨 `0000` 后，dialplan 只设置 `fcc_flow_entry=PHONE_BINDING` 并 `park`，不得提前 `answer`；Sidecar 必须同时上报该标记和真实 `sip_auth_username`。fcc-server 下发 `FNode.Answer`，并等待真实 `Event.Channel/ANSWERED` 后再收号。Caller-ID、单独的被叫号码或 Java 推测值都不能代替这些事实。
- Java 只发送固定业务文案的 `media.type=TEXT` 收号命令，不配置 FreeSWITCH 音频路径。`TTS_WORK_DIR` 必须是 Sidecar 可写、FreeSWITCH 可读的同一共享目录。
- `Event.DTMF.source=KEY_PRESS` 只用于观测物理逐键，不能触发数据库换绑；完整工号只来自 `FNode.ReadDTMF` 对应的 `Event.CommandResult.result.dtmf`。
- 完整工号处理完成后必须听到明确的成功或失败 TEXT 播报；播放完成结果到达后 fcc-server 再单独下发 `FNode.Hangup`，不能只写数据库后静默断线，也不能把同步受理当成播放完成。
- Java、Sidecar 和 FreeSWITCH 对录音/TTS 共享目录必须看到同一文件；验证路径边界、权限、剩余空间和原子写入。
- 导航语音可发送文案，Sidecar 负责 TTS；服务评价和结束语音使用受控预设。供应商密钥只存在 Sidecar 部署环境。
- 终端注册投影用于观察和诊断，不作为拨号同步硬前置；最终可达性由命令应答和 Channel 事件确认。

### 3.3 自动外呼

- 初次部署保持自动外呼关闭，配置号码、context、时段、频控和并发限制后再启用。
- 渐进式外呼必须先占用并呼叫坐席，坐席就绪后才呼客户；通知型直接呼客户并执行放音/按键确认。
- 暂停或取消只影响尚未开始的尝试，不默认强拆已建立通话。
- 结果未知时先对账 Call/Command/Event，不通过重复点击创建新任务。

## 4. 验收矩阵

| 领域 | 必测用例 | 通过证据 |
| --- | --- | --- |
| 身份与权限 | 未登录、停用账号、越权坐席、客户/录音越权、伪造工号 | HTTP/WS 明确拒绝，审计记录不泄露敏感数据 |
| 物理话机绑定 | 成功、缺少入口标记、缺少认证分机、错误工号、未知/停用分机、逐键/完整 DTMF、重复事件、并发换绑、通话中换绑 | 非可信 `0000` 只拒绝不写绑定；可信链路固定模型版本并使用 TEXT TTS；绑定、活跃终端和审计一致且只有一个有效归属 |
| 呼入 IVR | DID 命中、菜单按键、else/超时、无人、客户提前挂机 | 固定 flow version、阶段轨迹、Call/Leg/Command/Event 可关联 |
| 系统先呼坐席外呼 | 坐席接听/拒接/超时、客户忙/无应答、双方挂机 | 未接坐席时不呼客户；Bridge、双向音频、CDR 和释放状态一致 |
| 坐席终端主动外呼 | 已认证终端拨号、未绑定终端、号码转换、重复/乱序事件 | 系统接管已有坐席 Leg，不再次拨坐席；客户 Leg 与 Bridge 正确 |
| WebRTC | 注册成功/失败、麦克风拒绝、ICE 失败、来去电、第二来电、远端挂机 | SIP Session、业务 Call、Leg 和 UI 状态一致，真实双向音频可听 |
| 录音与评价 | 开始/停止、坐席先挂、客户先挂、评分/超时、文件缺失 | 评价和结束语音分支正确；元数据与最终文件、时长、权限一致 |
| 自动外呼 | 通知确认/未确认；渐进式坐席忙/就绪；暂停、取消、重试 | 每次尝试单独留痕，多实例或重启不重复拨号，成功项不重试 |
| Windows 弹屏 | 前台、最小化、托盘、锁屏、专注助手、断网重连、退出重登 | 收到/展示/激活/不支持可区分；过期提醒不补发，通知不泄露客户详情 |
| 故障恢复 | 断开 NATS、停止 Java、重启 Sidecar/FreeSWITCH、事件重复或乱序 | 保留稳定身份；终态不回退；占用不重复释放；未知状态进入对账而非假成功 |
| 管理端 | 流程首个草稿、保存、发布、并发发布、DID 绑定、客户和外呼管理 | 一个流程最多一个发布版本；失败不覆盖草稿；列表分页、详情按需加载 |

每条真实呼叫至少保存三类脱敏证据：命令受理或失败、FreeSWITCH 最终事件、媒体/用户实际结果。三者不能互相替代。

## 5. 证据记录模板

```text
用例：
环境与版本：
前置配置（不含凭据和真实个人号码）：
操作步骤：
预期结果：
实际结果：
关联 callId / channelUuid / commandId / eventId：
自动化结果：
真实媒体或 Windows 结果：
未验证项与原因：
结论：通过 / 失败 / 阻塞
```

## 6. 上线限制

- 当前开发阶段只支持空库基线，不支持旧库升级。进入生产变更管理后再建立不可变迁移和回退演练。
- Sidecar 运维 HTTP/CLI 当前仍依赖可信网络或上游认证网关；未补齐原生授权与持久审计前不得暴露到公网。
- 未完成签名、升级恢复和 Windows 实机验收前，Electron 包只能称为测试客户端。
- 未完成真实 TTS、录音完成态、双向媒体、事件重投和重启恢复前，不能宣称端到端可用。
