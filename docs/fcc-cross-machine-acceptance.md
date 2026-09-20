# FCC 跨电脑部署与验收

更新：2026-09-20。本文对应当前源码和本地证据，不是生产验收通过证明。FreeSWITCH、SIP、双向音频与 Windows 实际通知行为由目标电脑验收。

## 部署顺序

1. 备份已有开发数据库。新空库只执行 `chandler26-jdk21-fcc/docs/fcc-schema.sql`，不要再叠加同版本增量脚本。
2. 从本次修改前的基线升级时，依次执行 `docs/migrations/20260920_customer.sql`、`20260920_phone_binding.sql`、`20260920_dial_jobs.sql`、`20260920_event_inbox.sql`、`20260920_screen_pop_delivery.sql`、`20260920_callback_dispatch.sql`。这些脚本无旧产品数据导入；包含 ALTER 的脚本只执行一次。保留执行记录，失败后先检查表结构再续跑，不能整批盲重跑。
3. 启动 MySQL 8、Redis、Sidecar 所需 PostgreSQL 与启用 JetStream 的 NATS。NATS 使用持久存储。以受控管理员身份执行 `nats stream add --config chandler26-jdk21-fcc/docs/fcc-events-stream.json`，已有流先用 `nats stream info FCC_EVENTS` 检查，不自动覆盖配置。该文件是单节点测试配置，生产集群需按部署拓扑调整副本数。
4. Sidecar 每节点配置独占持久目录 `COMMAND_JOURNAL_DIR`、`EVENT_OUTBOX_DIR`，不能放临时盘、共享给另一进程或发布时清空。配置真实 `NODE_ID`、ESL、NATS 和 PostgreSQL；先验证节点健康，再启动 Java。
5. 配置 Java 的 `FCC_DEFAULT_NODE_ID` 与 Sidecar 完全一致。通过环境注入数据库凭据、Redis、`NATS_URL`、`FCC_ADMIN_BASE_URL`、`FCC_WS_ALLOWED_ORIGINS`、录音共享路径。当前运行模板只部署一个活跃 fcc-server；共享 durable consumer 尚不支持安全的多实例内存会话分配。
6. 配置 HTTPS 反向代理：`/api/admin` 到管理服务 8089，`/api/telephony` 和 `/ws/agent` 到控制服务 8085；WS 转发必须支持 Upgrade。管理、业务和节点运维入口保持分离。
7. 部署坐席 Vue 页面，安装客户端 `chandler26-fcc-client-web/release/current/FCC Agent Setup 2.0.0.exe`。首次启动填写该页面的 HTTPS 地址。仅本机开发允许 HTTP 回环地址；测试包未签名，不能宣称已完成可信发布。客户端以实体话机通话，不开放麦克风权限。

迁移已在一次性 MySQL 8 验证空库初始化和旧基线升级。回退应用前停用调度并排空呼叫；不要删新增事实表或命令目录。若需回退数据库，使用已验证备份恢复到隔离库并核对本次新增事实，禁止直接覆盖仍在写入的库。

## 标识与升级注意事项

业务 callId 为无前缀正整数雪花 ID，HTTP/WS 以字符串传递，所有关联表使用同一 BIGINT 数值。Channel UUID 独立生成，采用标准 36 位 UUID。非法业务标识直接拒绝，不兼容 `call-` 或哈希映射。旧实验产生的前缀上下文不能直接恢复：升级前排空实验呼叫并归档；新环境使用新基线，禁止搬运参考项目话单。

## 实体话机与业务配置

- 在管理端配置真实坐席、启用分机、DID、已发布的直达流程或 `group:技能组代码` 路由，以及外呼主叫和中继。不导入参考项目账户或演示话单。
- `FCC_BINDING_PROMPT_FILE` 为 FreeSWITCH 可读取的提示音绝对路径，提示用户输入工作台显示的八位验证码。0000 的 FreeSWITCH dialplan 必须把已认证 SIP 话机呼叫 answer/park 给 Sidecar 事件链路；保留真实 `sip_auth_username`，不要从 Caller-ID 伪造。工作台登录→输入分机→生成两分钟验证码→该话机拨 0000 输入验证码→刷新绑定结果。
- 只有绑定完成且工作台状态 READY 的坐席参与外呼预占。人工/渐进式外呼先拨坐席，接听后再拨客户，双方就绪才请求桥接；命令 ACCEPTED 不表示通话已经接通。
- 通知型需要 `FCC_NOTIFICATION_FILE`，提示“按 1 确认”，FreeSWITCH 必须可读该文件。通知应答与按键确认是不同结果。
- 自动外呼默认关闭。配置完成后设置 `FCC_OUTBOUND_ENABLED=true`；默认同时 5 次、Asia/Shanghai 9–18 时、同租户同号码 24 小时最多 3 次。任务暂停只作用于尚未执行的任务；取消不会强制挂断已开始的通话。未知结果不得人工连续点击创建重复任务。

## 验收用例及证据

| 用例 | 应检查的结果 |
| --- | --- |
| 绑定失败、换座、并发绑定 | 无验证码/过期码失败；三处绑定数据一致；通话中的坐席不能被换绑 |
| 呼入直达与技能组 | 正确租户和坐席；两通来电不能占用同一坐席；无人接听换人；客户提前挂机不再分配 |
| 人工与渐进式外呼 | 坐席先响；拒接不拨客户；客户忙线/超时产生明确结果；双方挂机只释放一次 |
| 通知外呼 | 提示音可听；按 1 与未确认结果不同；仅允许的失败原因重试，成功项不重拨 |
| Windows | 前台、最小化、托盘、锁屏、专注助手、断网重连、退出重登；提醒点击恢复窗口；接听/挂机后不保留旧提醒 |
| 故障恢复 | 分别断开 NATS、停止 Java、重启 Sidecar；保留同一 callId/channelUuid/commandId/eventId；检查重复拨号与未释放占用 |
| 权限 | 未登录、跨坐席、跨租户客户查询/修改被拒绝；通知不泄露客户详情 |

每条用例保留脱敏后的 Call、Leg、任务尝试、命令和事件关联。记录“指令受理”“FS 最终事件”“音频实际可听”三个证据，不能互相替代。不要在问题截图和日志中附口令、令牌或验证码。

## 本地证据与剩余阻断

已验证：Java 编译；客户、绑定、占用、外呼任务、Inbox 的真实 MySQL SQL；自动外呼原子回填/重复回填/保留取消状态；Sidecar 8 个生产包；真实本机 JetStream 下磁盘队列重建、同毫秒事件顺序、ACK 后清理；两前端构建；Electron 桥接测试及 NSIS 打包。

Java 全量回归：JDK 21 编译通过；56 项测试中 54 项通过、2 项需真实坐席凭据的 WebSocket 用例跳过，无失败或错误。运行依赖为一次性 MySQL 8.0.44、NATS 2.15 JetStream 和本机 Redis 5.0.14（不等于生产 Redis 7 验证）。Windows JDK 使用纯英文目录参数 `-Djdk.net.unixdomain.tmpdir=<本机英文临时目录>` 解决本机回环连接创建问题。话务集成测试使用规范事件及模拟 Sidecar RPC，不代表 ESL 或音频通过。真实 FreeSWITCH/SIP/媒体、认证后的浏览器交互和 Windows 通知尚未验收。

派发租约已实现：领取后两分钟未写 Call 的任务会失败并记录 DISPATCH_EXPIRED；迟到线程必须通过同事务租约校验才可保存 Call。已存在 Call 的尝试不按租约过期重拨。

新增闭环：服务端持久保存弹屏，重连仅补发本人本租户仍在振铃且未过期的提醒，记录 RECEIVED/SHOWN/ACTIVATED/UNSUPPORTED 回执；话后小结保存成功才结束整理；回拨领取与创建渐进式任务同事务提交，重复领取返回现有待执行任务。渐进式任务等待本人坐席就绪，不因忙碌消耗尝试次数。

恢复范围：Java 每 15 秒查询 ChannelSnapshot，双方话道持续 120 秒在成功快照中缺失后按 RECOVERY_UNKNOWN 结束并释放占用；查询失败不推断挂机。重连恢复不覆盖已存在的内存会话。Leg 状态不会被迟到振铃回退，小结不会被后续会话保存覆盖。

仍有边界：副作用与 Inbox 不是同一事务；FAILED/UNKNOWN 事件需人工核对，当前快照不能重建桥接事实或解决单边残留话道，也不自动重发未知命令。Windows 包尚未签名，当前采用人工安装新版，不支持自动升级。不得以本地测试替代这些能力的实现或真实环境验收。

JetStream 文件示例保留七天、最多 10 GiB，满额拒绝新消息以保留本地 outbox。必须监控容量和消费滞后：超出保留期仍可能丢失未消费历史；本地盘满会停止 Sidecar 事件入口。命令日志尚无安全清理工具，不能自行按时间删除未知命令记录。
