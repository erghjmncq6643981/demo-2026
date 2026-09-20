# FCC 硬编码治理现状

更新：2026-09-19。此项目是全新的 FreeSWITCH 呼叫中心，不承接参考项目的历史业务契约。旧版“允许演示 Seed / 假数据 / 固定坐席”的审计建议已废止。

## 已实施的源码治理

- Sidecar 仅注册规范 FNode 方法，移除 call.*、node.* 和 FNode.Bridge 别名。
- 录音事件统一为 Event.Recording，NATS 分类为 record；Java 和 Go 有契约测试源码。
- Java 流程参数使用 callId、ctrlId、channelUuid，桥接使用 guestChannelUuid / agentChannelUuid。缺少控制标识、目标话道或外呼号码时拒绝执行，不以 callId 兜底。
- 删除 CDR 中未调用的拼造执行轨迹方法。缺少客户资料、评分、路由依据时留空。
- 旧流程仿真接口已删除；通话过程只展示数据库中的模型快照和阶段执行事实，不生成空目标或模拟轨迹。
- Sidecar 启动只创建空表，不导入演示分机、网关或话单。
- 分机与网关响应不序列化密码；网关编辑留空密码时保留现有密码；XML 写入和重扫描错误不能返回全部成功。
- 运维端删除固定终端、网关、版本、运行状态、静态启动日志和假延迟。注册桥接选项取自查询；失败显示陈旧或不可用。
- 管理端分机视图已拆成 extensions、settings、fleet 功能模块，系统配置与客户端治理接真实 API。
- Java 节点 ID 由 FCC_DEFAULT_NODE_ID 提供，必须与 Sidecar NODE_ID 一致。

## 已验证与未验证

- 管理端、坐席端和运维端构建通过；运维端追加 Vue/TypeScript 检查通过。
- 坐席端 4 个测试文件、10 个测试通过。
- 管理端 4 个模型测试通过，覆盖流程 JSON、组树与 CDR 缺失/零值映射。
- 管理端 `CdrReportView.vue` 格式化后为 1049 行，仍超过 1000 行治理阈值；其状态和接口调用已提取到 `useCdrReport`，后续继续按列表、回拨和详情弹窗拆分。坐席端与运维端本轮未新增超阈值文件。
- 使用本机 JDK 21 后 Java 编译、Flow/Action/Event Inbox 等 20 个定向测试通过。本机 NATS Server 2.15.0 与 `FCC_EVENTS` JetStream 流已启动，应用测试日志确认连接成功；完整 `mvn -q test` 中 `fcc-server` 共运行 38 项，0 项断言失败、9 项环境错误、1 项跳过，仍因 MySQL JDBC、Redis 和 Windows loopback 建连错误不能记为全量通过。新增契约修复及部署配置见 [处理记录](../../docs/fcc-contract-remediation.md)。
- Go 构建和测试被工具链阻塞：本机 Go 1.25.4，项目声明 1.27.1，自动下载失败。
- 未完成真实 PostgreSQL、MySQL、Redis、Sidecar 事件写入/重投、ESL、SIP 和媒体端到端验证。NATS 传输可连接不代表真实通话成功。

## 剩余工程债务

- 管理端流程已改为真实 JSON 定义编辑器；CDR 与组管理已提取状态 composable 和纯模型。CDR/组视图仍超过 600 行，后续应继续按列表/详情拆分；登录态浏览器回归未完成。
- 运维端 ExtensionsView.vue 仍超过 600 行，查询、分页与密码表单可进一步提取。
- Sidecar 原生授权和完整审计仍不完整；事件已有稳定 ID、outbox、JetStream 与 Java Inbox，但副作用和 Inbox 非同事务，真实重投及故障恢复尚未验收。
- 原生模块查询与完整 SIP Profile 结构化状态尚未接入，界面明确显示未知。
- 管理端主包约 1.1 MB（压缩前），构建仍提示超过 500 kB；需继续优化依赖与分包。
- 真实网关 RTT 尚未实现；ESL 指令耗时不作为 SIP RTT 返回。
- 数据库空值/默认值调整与验证边界见 Sidecar docs/GOVERNANCE_VERIFICATION.md。

以上债务尚未完成，不能将本次治理描述为全项目已清零。
