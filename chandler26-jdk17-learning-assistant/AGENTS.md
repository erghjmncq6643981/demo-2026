# Backend Rules

The parent `AGENTS.md` also applies.

## Structure

- Organize the modular monolith by `identity`, `vocabulary`, `learning`, `reading`, `task`, and `system`; each owns `api`, `application`, `domain`, and `infrastructure`.
- AI belongs under `ai/{agent,model,chat,prompt}` with the same layers; provider clients, adapters, parsers, and protocols belong in `ai/gateway/**`.
- Keep HTTP code split into `api/controller`, `api/request`, and `api/response`; do not mix controllers and transport DTOs in the same package. Every serializable request/response field requires a business-readable Chinese `@Schema(description = "...")`.
- `application` owns use-case services, transaction boundaries, orchestration, DTO assembly, and event publication. Do not recreate a horizontal root `service` package; split large application code further by command, query, assembler, task, policy, or coordinator.
- Within `domain`, place persistence entities in `domain/entity`, aggregation/query business objects in `domain/bo`, enums in `domain/enums`, and domain rules/constants in `domain/constant`. Java package names must not use the reserved keyword `do`.
- Put MyBatis Mapper interfaces exclusively in `infrastructure/mapper`; Mapper XML namespaces must use that package. Do not place Mapper interfaces in a domain or infrastructure root package.
- Split Spring web concerns under `config/web`: annotations in `annotation`, AOP aspects in `aspect`, `@Configuration` classes in `configuration`, and servlet filters in `filter`. Keep other configuration categories similarly explicit.
- Enforce `api -> application -> domain` and `infrastructure -> domain`. Controllers never access Mappers; domain code never depends on API/infrastructure.
- An application class accesses infrastructure only inside its own domain. Cross-domain access uses a narrow application contract; update ArchUnit for intentional new boundaries.
- `common` is stable cross-domain foundations only. Keep Spring wiring in `config` and auth/JWT/secrets in `security`.
- At 1000 production lines, perform mandatory design review and split by use case, policy, persistence assembly, response assembly, or orchestration.

## API And Persistence

- Use Spring Security + JWT. Serialize `Long` IDs as JSON strings through shared Jackson. Throw `LearningAssistantException`, not generic runtime errors.
- Authenticated Controller、应用服务和审计基础设施必须通过 `CurrentUserContext` 获取当前用户；JWT 只允许由 `JwtAuthenticationFilter` 解析一次，不得重复传递 `Authorization` 参数、重新解析令牌或再次查询用户。
- 需要特定权限的 Controller 接口必须用 `@RequirePermission(LearningPermission.…)` 声明；权限由 `PermissionAuthorizationAspect` 在进入方法前统一校验。禁止在 Controller 方法体中散落 `requireAdmin()` 或角色编码比较；需要记录操作人的接口在注解校验后通过 `CurrentUserContext.requireUser()` 获取用户。个人与管理员共享的资源接口只能通过 `CurrentUserContext` 的权限语义决定数据范围，不能直接比较角色编码。
- `LearningErrorCode` owns stable code/status/default Chinese text. Domain and technical constants must live in the narrowest responsible package; never rebuild a cross-domain "all constants" facade. Logs are readable business info; diagnostics and stacks are debug-level.
- 所有新增或改造的日志必须先按用途分流：接口技术访问日志由 `ApiAccessLogAspect` 通过 SLF4J 记录，禁止写入 `learning_system_log`；用户可见、可追溯的业务操作和前端交互审计日志必须经 `SystemLogService` 记录。不得在业务代码中直接调用系统日志 Mapper，也不得以同步插入最终日志表替代审计服务。
- `SystemLogService` 只负责写入日志 Outbox 并发布 `SystemLogRecordedEvent`；提交后异步监听器批量、幂等地落库，恢复调度器重试未消费 Outbox。日志 Outbox 写入、监听、持久化或执行器拒绝异常必须被隔离，不能回滚已经成功的业务动作或改变接口业务结果。
- 异步日志与异步任务必须通过统一执行器传播 MDC 中的请求/链路上下文。业务事件使用包含用户、业务对象、结果的结构化 info 日志；异常堆栈、SQL、HTTP 请求/响应体只允许 debug 级别。API Key、密码、JWT、完整 Prompt、完整模型响应及其他敏感内容不得写入日志；必要诊断值必须脱敏且截断。
- Use MyBatis-Plus for ordinary CRUD. Put custom SQL in `src/main/resources/mapper/*.xml` with Mapper method, explicit parameters, and XML validation.
- Never issue SQL in an iteration. Prefer join/aggregate SQL or explicit batch queries; batch homogeneous writes with bounded chunks. Paginate growable list APIs and keep their DTOs small.
- Init SQL is in `src/main/resources/db/init`, schemas in `db/schema`, and order in `db/README.md`. Never rewrite executed migration intent; add numbered upgrades.

## Transactions, AI, Learning

- Never call AI/HTTP inside a transaction. Persist/claim work first, publish after-commit events, and execute on bounded `aiTaskExecutor`.
- Retryable work has atomic `pending -> running` claim, item states, idempotent writes, terminal outcomes, and failed-item retry only. Protect duplicate submission/generation with locks or uniqueness.
- Every model request sets `AiInvocationScene`; independent actions such as vocabulary analysis, card generation, scene generation, and reading generation send only concise action-specific context. A long-running plan must not carry historical messages into later independent calls; reuse a chat session only when the current learning scene genuinely needs continuous context.
- Provider, model, API protocol, request adapter, response parser, context window, and output limit are model capability metadata and must be represented by the relevant enum/definition. Select preprocessing and request-body construction through the configured request adapter, then select structured-response handling through the configured response parser; do not scatter provider/model `if` branches across business services.
- Token budgeting must use the selected model's actual context window and reserve output space. Reject or split a request before the estimated input plus requested output reaches 90 percent of the effective context capacity; never rely on a global hard-coded context limit. Model connection tests must call the configured provider directly with a minimal request, not trigger an Agent or learning workflow.
- Validate prompt placeholders, parse structured responses defensively, and enforce business invariants. Mask/bound AI audit, prompt, response, and provider-error storage; an audit persistence failure must never replace a successful model result.
- Cards are shared cache data; wordbook entries preserve personal snapshots. Related words are synonym/antonym/family only; collocations stay separate.
- Materials remain historical. Unfinished words may reappear as review words but keep original attribution. Catalog analysis accepts partial responses. Scenes exclude earlier core words and split daily core words evenly into materials of at most 50.

## Verification

- Run compile and tests after Java changes; after dependency changes also run `mvn -q dependency:analyze -DignoreNonCompile`.
- Add Chinese comments to public DTO/entity fields and complex boundaries. Use a real database for async/database workflows when available and state any limitation.
