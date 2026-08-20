# 工程治理已落地项

本文只记录当前代码已实现且有门禁验证的治理能力。

## 架构边界

- 后端按 `identity`、`vocabulary`、`learning`、`reading`、`task`、`system` 业务域组织，每个域内部使用 `api/application/domain/infrastructure` 分层；AI 代码按 `agent/model/chat/prompt/gateway` 分域，供应商 HTTP 协议、请求适配和响应解析集中在 `ai/gateway`。
- ArchUnit 禁止 Controller/API 直连 Mapper，禁止 Entity 反向依赖 Service/API/Infrastructure，禁止 Application 跨域访问其他业务域的 Mapper，并阻止重新创建根级 `controller/service/mapper` 横向目录。
- 学习计划响应由 `LearningPlanResponseAssembler` 批量装配；选词由 `LearningPlanVocabularySelector` 负责；复习时间由 `ReviewSchedulePolicy` 负责。
- 前端功能代码位于 `public/src/features`，按 `identity`、`vocabulary`、`learning`、`reading`、`ai`、`task`、`system` 业务域组织；共享能力位于 `public/src/shared`，入口 `app.js` 只负责 wiring。场景计划和语境精读已拆出纯业务模型、预览数据与 API 网关。

## 数据库与事务

- Flyway 管理版本：空库执行 `V1__BaselineSchema`，存量非空库以 107 建立基线，后续迁移从 V108 开始。
- 当前完整 schema、种子数据、历史升级脚本相互分离，执行顺序见 `src/main/resources/db/README.md`。
- 学习计划详情一次批量加载单元、材料、词条、进度和检测记录，避免逐单元 N+1。
- 同类更新使用批量 SQL；大批次在服务层分块。
- AI 网络调用不持有数据库事务。场景材料、词卡和词本分析通过显式任务状态异步执行，支持部分成功、取消和失败项重试。

## AI 交互

- 每次调用必须声明 `AiInvocationScene`；固定动作只发送必要变量，不附带历史对话。
- Provider Parser 先解析供应商 envelope，`AiSceneResponseCodecRegistry` 再按场景解包、归一别名和校验必需根字段，业务服务直接消费 `JsonNode`。
- 上下文预算按模型能力计算并在安全阈值前拒绝；模型 HTTP 日志不输出完整响应正文。
- AI 审计默认仅保存元数据、Token 和耗时。Prometheus 暴露调用次数、失败数、耗时和 Token 指标。
- AI 线程池大小、队列、存活时间和停机等待时间均可配置，拒绝策略不会回退到请求线程执行昂贵模型任务。

## 安全与可观测性

- Spring Security + JWT 保护业务接口，模型 API Key 使用后端加密存储。
- `prod/pre` 启动时拒绝开发默认 JWT/API Key 密钥。
- Actuator 暴露 `health`、`info`、`prometheus`；业务日志使用可读中文，技术细节和堆栈位于 DEBUG。
- 后端 `Long` ID 统一序列化为字符串，前端把所有 ID 当作不透明字符串处理。

## 自动门禁

GitHub Actions 对每个 push 和 PR 执行：

- 后端：测试、编译、依赖分析、ArchUnit 和可用时的 Testcontainers MySQL 冒烟测试。
- 前端：模块导入检查、ESLint、Vitest、静态构建、桌面与移动端 Playwright 冒烟测试。
- 本地最低命令与提交前检查以根目录 `AGENTS.md` 为准。
