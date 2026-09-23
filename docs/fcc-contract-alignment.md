# FCC 前后端契约对齐现状

更新：2026-09-19。原检查发现的七项高优先级问题已进行源码修复或明确禁用；部署顺序、验证证据和未完成边界见 [本轮处理记录](fcc-contract-remediation.md)。不把单元测试或命令受理等同于真实通话完成。

## 已处理项

| 编号 | 当前对齐行为 |
| --- | --- |
| A01 | 转接只返回 ACCEPTED；失败/未知不退出工作区，不提前拆坐席或推假挂机 |
| A02 | 保持不再返回请求值冒充媒体状态；前端显示请求状态和待确认提示 |
| A03 | 班长四类干预尚未实现，前端禁用、后端 501；他人实时状态未知 |
| A04 | callId 必填、认证主体归属校验、明确节点；不取全局最近会话 |
| A05 | REST 携带令牌，WS 握手/收发在线核验管理端身份；URL 工号不能用于认证 |
| A06 | 本人专属 SIP 配置接口、禁止缓存、仅内存使用、AES-GCM 密文，不再使用构建密码 |
| F01/F04 | 保存、发布和运行加载统一验证固定阶段 IVR；拒绝旧 DID_DIRECT、未实现能力和未知字段 |
| F02/F03 | 提交后通知、服务间鉴权、编译失败返回失败并保留旧节点；检查异步通知响应 |
| F05/F06 | 流程/版本列表均为分页摘要，完整定义按需加载；旧仿真接口已移除，通话过程只展示持久执行事实 |
| B01 | 客户和自动外呼只在 admin-web 维护/查看；fcc-admin 校验 `business:manage` 后转发当前令牌，fcc-server 在线复核数据库当前控制台身份和权限后保存事实、调度并执行；坐席端管理工作台已移除 |

证据入口：[控制用例](../chandler26-jdk21-fcc/fcc-server/src/main/java/com/chandler/fcc/server/telephony/application/CallControlService.java)、[身份验证](../chandler26-jdk21-fcc/fcc-server/src/main/java/com/chandler/fcc/server/telephony/application/AgentIdentityService.java)、[SIP 配置](../chandler26-jdk21-fcc/fcc-admin/src/main/java/com/chandler/fcc/admin/service/AgentSipConfigService.java)、[共享定义校验](../chandler26-jdk21-fcc/fcc-common/src/main/java/com/chandler/fcc/common/protocol/FlowDefinitionValidator.java)、[Flow Studio 服务](../chandler26-jdk21-fcc/fcc-admin/src/main/java/com/chandler/fcc/admin/flow/application/FlowStudioService.java)、[动作执行入口](../chandler26-jdk21-fcc/fcc-server/src/main/java/com/chandler/fcc/server/flow/application/FlowActionExecutionService.java)。

## 尚未闭环

- 发布通知仍是 best-effort；没有持久 Outbox、重试队列、每实例激活看板或运行通话版本固定证明。
- 保持媒体完成态、转接后 Leg/Bridge 生命周期以及重启后的恢复需真实联调；ACCEPTED 是请求已受理。
- CDR 方向/状态仍需进一步统一：坐席类型缺少 INTERNAL，管理端缺失方向默认 INBOUND、未知状态归 MISSED。不能把这些视为已清零。
- Long 序列化模块覆盖 id/*Id 的 Bean 属性；Map、其他命名和实际 Spring HTTP 输出仍需全量契约验证。
- SIP 凭据轮换、分机开通失败重试与跨系统补偿尚不完整。旧明文凭据明确拒绝，需要重新开通或受控轮换，不自动兼容。
- WS 每次业务收发在线核验，安全边界已收紧，但增加认证服务延迟依赖；后续可采用可撤销短期票据。
- 真实录音、权限范围、回拨、组织报表、客户端治理等接口尚未完成全量浏览器和环境回归。

## 已核对的基础契约

坐席 outbound/hangup/hold/dtmf/supervise/transfer 与 Java 路径对应；流程版本和草稿字段对应。DTMF 已统一从控制面发送，仍需验证目标话道实际收号。录音规范为 Event.Recording、category=record，Sidecar 保留规范 FNode 方法。

2026-09-23：JDK 21 编译、`fcc-common` 全量测试以及录音、评价、拨号超时、事件分发、流程缓存和话务控制定向测试通过；Sidecar 全部生产包测试与构建通过。验证时临时启动 NATS Server 2.15.0 与 JetStream，应用连接成功；完整 `mvn -q test` 中 `fcc-server` 共运行 48 项，0 项断言失败、9 项环境错误、1 项跳过，仍因 Windows/JDK selector 无法建立 Lettuce Redis 事件循环而未通过，MySQL 业务 Schema 未完成本轮验证。没有全量接口无差异或端到端通过结论。
