# Backend Rules

The parent `AGENTS.md` also applies.

## Structure

- Organize the modular monolith by `identity`, `vocabulary`, `learning`, `reading`, `task`, and `system`; each owns `api`, `application`, `domain`, and `infrastructure`.
- AI belongs under `ai/{agent,model,chat,prompt}` with the same layers; provider clients, adapters, parsers, and protocols belong in `ai/gateway/**`.
- Enforce `api -> application -> domain` and `infrastructure -> domain`. Controllers never access Mappers; domain code never depends on API/infrastructure.
- An application class accesses infrastructure only inside its own domain. Cross-domain access uses a narrow application contract; update ArchUnit for intentional new boundaries.
- `common` is stable cross-domain foundations only. Keep Spring wiring in `config` and auth/JWT/secrets in `security`.
- At 1000 production lines, perform mandatory design review and split by use case, policy, persistence assembly, response assembly, or orchestration.

## API And Persistence

- Use Spring Security + JWT. Serialize `Long` IDs as JSON strings through shared Jackson. Throw `LearningAssistantException`, not generic runtime errors.
- `LearningConstants.ErrorCode` owns stable code/status/default Chinese text. Logs are readable business info; diagnostics and stacks are debug-level.
- Use MyBatis-Plus for ordinary CRUD. Put custom SQL in `src/main/resources/mapper/*.xml` with Mapper method, explicit parameters, and XML validation.
- Never issue SQL in an iteration. Prefer join/aggregate SQL or explicit batch queries; batch homogeneous writes with bounded chunks. Paginate growable list APIs and keep their DTOs small.
- Init SQL is in `src/main/resources/db/init`, schemas in `db/schema`, and order in `db/README.md`. Never rewrite executed migration intent; add numbered upgrades.

## Transactions, AI, Learning

- Never call AI/HTTP inside a transaction. Persist/claim work first, publish after-commit events, and execute on bounded `aiTaskExecutor`.
- Retryable work has atomic `pending -> running` claim, item states, idempotent writes, terminal outcomes, and failed-item retry only. Protect duplicate submission/generation with locks or uniqueness.
- Every model request sets `AiInvocationScene`; independent batch actions get concise necessary context. Reuse one chat session per learning scene only.
- Validate prompt placeholders, parse structured responses defensively, and enforce business invariants. Mask/bound AI audit, prompt, response, and provider-error storage.
- Cards are shared cache data; wordbook entries preserve personal snapshots. Related words are synonym/antonym/family only; collocations stay separate.
- Materials remain historical. Unfinished words may reappear as review words but keep original attribution. Catalog analysis accepts partial responses. Scenes exclude earlier core words and split daily core words evenly into materials of at most 50.

## Verification

- Run compile and tests after Java changes; after dependency changes also run `mvn -q dependency:analyze -DignoreNonCompile`.
- Add Chinese comments to public DTO/entity fields and complex boundaries. Use a real database for async/database workflows when available and state any limitation.
