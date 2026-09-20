# FCC 跨电脑部署与验收

更新：2026-09-20。本文对应当前源码和本地证据，不是生产验收通过证明。FreeSWITCH、SIP、双向音频与 Windows 实际通知行为由目标电脑验收。

## 部署顺序

1. 这是全新项目，只支持空库初始化。备份已有开发数据后创建空 MySQL 8 数据库，执行 `chandler26-jdk21-fcc/docs/fcc-schema.sql`；不要导入旧呼叫中心表、话单或流程 JSON。仓库当前没有可执行增量迁移目录，不得按不存在的迁移文件升级旧库。
2. 启动 MySQL 8、Redis、Sidecar 所需 PostgreSQL 与启用 JetStream 的 NATS。NATS 使用持久存储。以受控管理员身份执行 `nats stream add --config chandler26-jdk21-fcc/docs/fcc-events-stream.json`，已有流先用 `nats stream info FCC_EVENTS` 检查，不自动覆盖配置。该文件是单节点测试配置，生产集群需按部署拓扑调整副本数。
3. Sidecar 每节点配置独占持久目录 `COMMAND_JOURNAL_DIR`、`EVENT_OUTBOX_DIR`，不能放临时盘、共享给另一进程或发布时清空。配置真实 `NODE_ID`、ESL、NATS 和 PostgreSQL；先验证节点健康，再启动 Java。
4. 配置 Java 的 `FCC_DEFAULT_NODE_ID` 与 Sidecar 完全一致。通过环境注入数据库凭据、Redis、`NATS_URL`、`FCC_ADMIN_BASE_URL`、`FCC_WS_ALLOWED_ORIGINS`、录音共享路径。当前运行模板只部署一个活跃 fcc-server；共享 durable consumer 尚不支持安全的多实例内存会话分配。
5. 配置 HTTPS 反向代理：`/api/admin` 到管理服务 8089，`/api/telephony` 和 `/ws/agent` 到控制服务 8085；WS 转发必须支持 Upgrade。管理、业务和节点运维入口保持分离。
6. 部署坐席 Vue 页面，安装客户端测试包。首次启动填写该页面的 HTTPS 地址。仅本机开发允许 HTTP 回环地址；测试包未签名，不能宣称已完成可信发布。客户端以实体话机通话，不开放麦克风权限。

本轮未连接一次性 MySQL 8 执行基线，也未验证旧库升级；只有静态 DDL 与源码检查。由于不提供旧库兼容，结构不一致的开发环境应重建，不应在运行中的库上临时删列。进入生产变更管理后必须引入不可变迁移、执行记录和隔离库回退验证。

## 标识与升级注意事项

业务 callId 为无前缀正整数雪花 ID，HTTP/WS 以字符串传递，所有关联表使用同一 BIGINT 数值。Channel UUID 独立生成，采用标准 36 位 UUID。非法业务标识直接拒绝，不兼容 `call-` 或哈希映射。旧实验产生的前缀上下文不能直接恢复：升级前排空实验呼叫并归档；新环境使用新基线，禁止搬运参考项目话单。

## 实体话机与业务配置

- 在管理端配置真实坐席、启用分机、DID、已发布的固定阶段 IVR 流程或 `group:技能组代码` 路由，以及外呼主叫和中继。不导入参考项目账户或演示话单。
- `FCC_BINDING_PROMPT_FILE` 为 FreeSWITCH 可读取的提示音绝对路径，提示用户输入坐席工号。`0000` 的 FreeSWITCH dialplan 必须把已认证 SIP 话机呼叫 answer/park 给 Sidecar 事件链路；保留真实 `sip_auth_username`，不从 Caller-ID 伪造。已认证话机拨 `0000`→输入坐席工号→fcc-server 锁定坐席与分机完成换绑→挂断。
- 只有绑定完成且工作台状态 READY 的坐席参与外呼预占。人工/渐进式外呼先拨坐席，接听后再拨客户，双方就绪才请求桥接；命令 ACCEPTED 不表示通话已经接通。
- 通知型需要 `FCC_NOTIFICATION_FILE`，提示“按 1 确认”，FreeSWITCH 必须可读该文件。通知应答与按键确认是不同结果。
- 自动外呼默认关闭。配置完成后设置 `FCC_OUTBOUND_ENABLED=true`；默认同时 5 次、Asia/Shanghai 9–18 时、同号码 24 小时最多 3 次。任务暂停只作用于尚未执行的任务；取消不会强制挂断已开始的通话。未知结果不得人工连续点击创建重复任务。

## 验收用例及证据

| 用例 | 应检查的结果 |
| --- | --- |
| 绑定失败、换座、并发绑定 | 无验证码/过期码失败；三处绑定数据一致；通话中的坐席不能被换绑 |
| 呼入 IVR 与技能组 | 命中正确坐席或技能组；两通来电不能占用同一坐席；无人接听换人；客户提前挂机不再分配 |
| 人工与渐进式外呼 | 坐席先响；拒接不拨客户；客户忙线/超时产生明确结果；双方挂机只释放一次 |
| 通知外呼 | 提示音可听；按 1 与未确认结果不同；仅允许的失败原因重试，成功项不重拨 |
| Windows | 前台、最小化、托盘、锁屏、专注助手、断网重连、退出重登；提醒点击恢复窗口；接听/挂机后不保留旧提醒 |
| 故障恢复 | 分别断开 NATS、停止 Java、重启 Sidecar；保留同一 callId/channelUuid/commandId/eventId；检查重复拨号与未释放占用 |
| 权限 | 未登录、越权坐席或客户查询/修改被拒绝；通知不泄露客户详情 |

每条用例保留脱敏后的 Call、Leg、任务尝试、命令和事件关联。记录“指令受理”“FS 最终事件”“音频实际可听”三个证据，不能互相替代。不要在问题截图和日志中附口令、令牌或验证码。

## 本地证据与剩余阻断

本轮已验证范围以最终交付记录为准：JDK 21 编译、20 项 Flow/Action/Event Inbox 定向测试、管理端构建、4 项治理测试、Mapper XML 解析和静态契约扫描。完整 Java 测试在 NATS 连接处失败。没有连接真实 MySQL、NATS/JetStream、Redis、Sidecar、FreeSWITCH 或 SIP 终端，也没有执行认证后的浏览器与 Windows 通知验收。单元测试和模拟 Sidecar RPC 不代表 ESL、音频或故障恢复通过。

派发租约已实现：领取后两分钟未写 Call 的任务会失败并记录 DISPATCH_EXPIRED；迟到线程必须通过同事务租约校验才可保存 Call。已存在 Call 的尝试不按租约过期重拨。

新增闭环：服务端持久保存弹屏，重连仅补发当前坐席仍在振铃且未过期的提醒，记录 RECEIVED/SHOWN/ACTIVATED/UNSUPPORTED 回执；话后小结保存成功才结束整理；回拨领取与创建渐进式任务同事务提交，重复领取返回现有待执行任务。渐进式任务等待目标坐席就绪，不因忙碌消耗尝试次数。

恢复范围：Java 每 15 秒查询 ChannelSnapshot，双方话道持续 120 秒在成功快照中缺失后按 RECOVERY_UNKNOWN 结束并释放占用；查询失败不推断挂机。重连恢复不覆盖已存在的内存会话。Leg 状态不会被迟到振铃回退，小结不会被后续会话保存覆盖。

事件消费边界：明确失败会 NAK，Inbox 最多重新领取 5 次；已处理重复事件直接 ACK；遗留 `PROCESSING` 与耗尽次数的 `FAILED` 转为 `UNKNOWN`。副作用与 Inbox 仍不是同一事务，`UNKNOWN` 事件需要人工或恢复任务核对；上述逻辑只有单元测试证据，尚未在真实 JetStream 验证重投。

其余边界：当前快照不能重建桥接事实或解决单边残留话道，也不自动重发未知命令。Windows 包尚未签名，当前采用人工安装新版，不支持自动升级。不得以本地测试替代这些能力的实现或真实环境验收。

JetStream 文件示例保留七天、最多 10 GiB，满额拒绝新消息以保留本地 outbox。必须监控容量和消费滞后：超出保留期仍可能丢失未消费历史；本地盘满会停止 Sidecar 事件入口。命令日志尚无安全清理工具，不能自行按时间删除未知命令记录。
