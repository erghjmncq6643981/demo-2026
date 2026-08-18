# 工程治理已落地项

本文只记录当前代码已经实现的治理约束，未实现的重构不写入本文。

## 数据库与事务

- 会话列表使用一次聚合查询返回消息数量，避免逐会话 `COUNT` 的 N+1 查询。
- 公共词表导入、疑似断词确认、词卡任务明细和词汇洞察支持批量 SQL；批量数据由服务层按 500 条分块。
- 自定义 SQL 位于 `src/main/resources/mapper/` XML 文件；简单单表查询继续使用 MyBatis-Plus。
- 学习计划创建先提交短事务，再调用 AI；场景材料解析通过短事务保存，AI 网络调用不在数据库事务内。
- 批量词卡任务通过 Job/Item 表记录，事务提交后异步执行，支持重复提交保护、失败重试和任务状态轮询。
- AI 批处理统一写入 `learning_ai_async_task`，由任务中心提供人工观察、取消、立即执行和重试；调度器按预约时间/低价窗口原子领取任务，领域 Job 继续保存逐词明细。
- `101_engineering_governance_mysql.sql` 为已有数据库增加 AI 会话消息序号唯一约束；新库完整结构直接使用 `schema/00_ai_schema_mysql.sql`。

## 错误码与日志

- `LearningConstants.ErrorCode` 是带默认中文消息和 HTTP 状态的枚举。
- 业务代码优先使用 `LearningAssistantException.badRequest(code)`、`unauthorized(code)` 或 `notFound(code)`；动态上下文只在必要处覆盖消息。
- 生产项目日志级别为 INFO；业务异常不默认输出堆栈，技术诊断位于 DEBUG。
- AI、外部服务和批处理日志包含 `event`、`result`、业务 ID、耗时和错误码，不记录完整 API Key、Prompt 或上游响应体。

## AI 调用

- AI HTTP 客户端配置连接和读取超时。
- 会话历史按消息数量和字符预算裁剪；损坏的会话变量会显式报错，不静默吞掉。
- AI 调用审计默认只存元数据和 Token 指标；受控排障环境可通过 `LEARNING_AI_AUDIT_STORE_CONTENT=true` 临时保存截断正文。
- AI 审计记录落库失败不会覆盖模型调用本身的成功或失败结果。
- 会话消息序号使用数据库唯一约束和有限次数冲突重试。

## 依赖与代码规范

- 保留源码实际使用的 Hutool；已确认未使用的 Guava、Fastjson、Commons Lang、Netty、OpenFeign、LoadBalancer 和 Caffeine 已移除。
- 公共 DTO、领域对象关键属性和复杂业务边界使用中文注释；简单私有转换方法不要求重复性注释。
- 新增状态值必须优先定义为枚举或集中常量，禁止在 Controller 和 SQL 中散落魔法字符串。

## 验证

后端最低验证：`mvn -q -DskipTests compile`、`mvn -q test`。前端变更模块至少执行 `node --check`。涉及异步任务时还需要验证统一任务状态从 `pending -> running -> completed/partial_failed/failed/cancelled` 的完整链路，并在前端任务中心核对进度和失败处理入口。
