# Learning Assistant Project Rules

## Scope

- Backend project: `chandler26-jdk17-learning-assistant`
- Frontend project: `chandler26-jdk17-learning-assistant-web`
- Treat both projects as one product. Changes in one side usually need API, DTO, SQL, or UI checks on the other side.

## General

- Inspect the existing code first and follow local patterns. Prefer `rg` / `rg --files` for discovery.
- Do not revert unrelated user changes. The worktree may be dirty.
- Use `apply_patch` for manual file edits.
- Keep implementation scoped to the requested behavior. Avoid opportunistic refactors.
- Never hardcode real API keys in source, docs, or SQL. Model API keys must be stored encrypted by the backend.
- Before editing a cross-layer feature, inspect both projects and the relevant DTO, controller, service, mapper, SQL, and frontend module.
- Do not stage or commit unrelated user changes. Preserve a dirty worktree and confirm the staged scope before publishing.

## Documentation

- Product and design documents must describe behavior that is implemented and verified in the current frontend and backend.
- Keep documentation concise and centered on product positioning, information architecture, core workflows, user-visible behavior, and important business rules.
- Remove stale proposals, session history, duplicated explanations, exhaustive code-derived field lists, and unimplemented roadmap items unless the user explicitly requests them.
- Maintain one clear source of truth for each topic. When behavior or naming changes, update the relevant document and remove the superseded description in the same change.
- Clearly distinguish preview/mock-only behavior from production behavior; never present mock data or an unfinished interaction as a completed backend capability.

## Frontend

- Keep feature code under `public/src/features/**`; shared utilities belong in `public/src/shared/**`.
- Organize frontend modules by business domain: `features/identity/**`, `features/vocabulary/**`, `features/learning/**`, `features/reading/**`, `features/ai/**`, `features/task/**`, and `features/system/**`. Within `features/ai/**`, keep Agent, model/provider, session, and prompt capabilities in separate subdirectories.
- Domain modules own their UI, API adapters, state, and pure models. Cross-domain composition belongs in the app facade or an explicit domain coordinator; do not copy another domain's implementation into `profile`, `system`, or `app.js`.
- Do not add new large logic to `public/app.js`; use feature modules and a small facade/wiring layer.
- Do not open or close modals by directly calling `classList.remove('hidden')` / `classList.add('hidden')`. Use `showModal` / `hideModal` from `/src/shared/modal.js` so scroll position resets.
- Normalize wordbook ids through `/src/shared/wordbook.js` helpers before API calls or state comparisons.
- Treat every backend `Long`/Snowflake identifier (`id`, `*Id`, `data-*-id`, select values, URL path/query IDs, and request DTO IDs) as an opaque string in the frontend. Never call `Number`, `parseInt`, arithmetic, or numeric sorting on an identifier; use `normalizeId`/`sameId` and `String(...)` when serializing or comparing. JSON request bodies may send the string directly because Spring deserializes it to `Long`.
- Before committing frontend changes, scan for ID coercion with `rg -n -i "(Number|parseInt|parseFloat)\\([^\\n]*(id|identifier)|\\b(id|[A-Za-z]+Id)\\s*:\\s*(Number|parseInt)" public/src` and manually review every match. Numeric business values such as counts, limits, sequence, indexes, dates, and scores are allowed only when they are not identifiers.
- All destructive actions and navigation that discards context need a secondary confirmation dialog.
- Keep the navigation and fixed card controls from scrolling away. Scroll only the intended content panel.
- Check desktop and mobile behavior for any layout or interaction change, especially review cards, modals, wordbook lists, and long buttons.
- Use qwerty-learner as visual inspiration: focused learning surface, keyboard-friendly interactions, restrained product UI.

## Backend

- Use Spring Security + JWT for authenticated APIs.
- Keep AI backend code under `ai/{agent,model,chat,prompt}` with `api`, `application`, `domain`, and `infrastructure` layers. External model HTTP clients, request adapters, response parsers, and provider protocols belong under `ai/gateway/**`.
- Organize non-AI backend code by business domain: `identity`, `vocabulary`, `learning`, `reading`, `task`, and `system`; each domain owns its `api`, `application`, `domain`, and `infrastructure` packages. Do not create new horizontal `controller`, `service`, `mapper`, or shared business `domain` packages.
- Enforce `api -> application -> domain` and `infrastructure -> domain`. Controllers must not access Mapper classes, domain classes must not depend on API or infrastructure, and cross-domain calls must go through application services rather than another domain's Mapper.
- Treat the backend as a modular monolith with physical package boundaries: an `application` class may access infrastructure only from its own business domain. Other domains expose narrow application-level access/query services; their Mapper and Entity implementation details must remain private to that domain.
- Do not recreate root-level `controller`, `service`, `mapper`, or shared business `domain` packages. New production and test classes must be placed under the matching business domain; Mapper XML validation tests belong under the database test domain.
- Any new cross-domain dependency requires an explicit application contract and an ArchUnit rule update when the boundary is intentional. Never bypass the boundary by importing another domain's `infrastructure` package, even inside a transaction.
- Reserve `common` for stable cross-domain foundations such as exceptions, web envelopes, persistence base classes, constants, and request context. Do not use `common` as a replacement for `support`, `utils`, or misplaced business logic; keep `config` for Spring wiring and `security` for authentication, authorization, JWT, principals, filters, and secret protection.
- Serialize backend `Long`/Snowflake IDs as JSON strings through the shared Jackson configuration; never introduce a custom `ObjectMapper` or DTO serializer that emits these IDs as JSON numbers. Request DTOs may keep `Long` fields because Jackson accepts the frontend string representation.
- Throw `LearningAssistantException` or a project-specific runtime exception instead of generic runtime errors.
- Technical diagnostics and stack traces should be debug-level; business events should be info-level and readable by business users, for example: `用户「小明」把单词「abandon」添加到单词本「默认单词本」`.
- Important user-facing operations should write both server logs and system log table records where appropriate.
- Avoid magic values. Put shared constants in `LearningConstants` only when the value has clear business meaning.
- Use MyBatis-Plus wrappers and existing mapper/service patterns.

## Engineering Governance

- Keep database transactions short. Never hold a database transaction open while calling an AI provider, HTTP service, or other slow external dependency.
- Split workflows into persistence and external-work phases. Persist the request/job first, publish an event inside that transaction, and execute external work after commit on the bounded `aiTaskExecutor`.
- Long-running or retryable work must have explicit Job/Item state, an atomic claim from `pending` to `running`, idempotent writes, terminal success/partial-failure/failure states, and retry of failed items only.
- Protect duplicate submissions at the business-resource boundary, preferably by locking the scene/unit row or using an equivalent database uniqueness invariant.
- Prefer one aggregate SQL query over per-row queries when a list includes counts or related summaries. Use batch insert/update SQL for homogeneous writes and chunk large batches; do not introduce N+1 loops in services.
- Put custom MyBatis SQL in `src/main/resources/mapper/*.xml`; keep simple CRUD and conditional queries in MyBatis-Plus wrappers. Every custom SQL must have a mapper method, clear parameters, and XML validation coverage.
- If a schema change may already have been executed, add a new numbered migration instead of rewriting migration history. Keep clean-database schema, seed data, and upgrade migrations separate.
- `LearningConstants.ErrorCode` is the single source for stable error codes, HTTP status, and default Chinese messages. Prefer `LearningAssistantException.*(code)`; only override a message when dynamic context is genuinely useful.
- Business logs use info-level structured events with user/business IDs and outcomes. Provider errors, response bodies, prompts, API keys, and stack traces must be truncated, masked, or debug-only. Preserve request trace/MDC context in async work.
- Every AI request must set an `AiInvocationScene` from the enum. Reuse one `ai_chat_session` for one learning scene; do not create a session per request. Validate prompt placeholders before saving templates, then parse and enforce business invariants on every structured response.
- AI audit records default to metadata/token/latency summaries. Storing prompt or response content requires an explicit controlled setting and a bounded length; audit persistence failure must not replace the model result.
- Preserve learning data invariants: generated scene materials remain traceable historical context; unfinished words may appear as `review_words` in later scenes without being counted as new core words; candidates exclude words already arranged as core words in the same plan; daily core words are evenly split into materials of at most 50 words.
- Add Chinese comments to public DTO/entity fields and complex business boundaries. Do not add repetitive comments to trivial private accessors or obvious conversions.
- Remove dependencies only after checking actual source usage and run dependency analysis after dependency changes. Prefer existing Hutool/Guava utilities where they materially simplify code; do not add a helper library for trivial code.

## AI Agent And Vocabulary

- Learning vocabulary is one scene. A user should reuse one `ai_chat_session` per learning scene instead of creating a new session for every AI request.
- AI-generated vocabulary records are shared cache data. When adding to a wordbook, save a personal snapshot on the wordbook entry so later regeneration by another user does not overwrite personal learning details.
- Related words should contain semantic relations only: synonym, antonym, and word family. Collocations stay in the collocation area, not in related words.
- AI prompt templates must preserve required placeholders such as `{{term}}`; validate placeholders before saving.
- Vocabulary card JSON should include definitions, examples with Chinese translations, collocations with meanings, related words with part of speech, meaning, and phonetics, and memory tips.
- Keep generated scene materials as traceable historical records. Unfinished words may be reused as review vocabulary in later scene articles, while their original learning records and core-word attribution remain unchanged.
- Public vocabulary catalog analysis accepts partial AI responses: persist every valid returned entry, leave missing or invalid entries unresolved, and retry only those unresolved entries in a later analysis task.
- New scene candidates must exclude every catalog word already arranged in any scene of the same plan, regardless of tier. Split a daily target evenly into scene materials with at most 50 core challenge words each; for example, 80 words becomes two 40-word materials and 120 words becomes three 40-word materials.

## Database

- MySQL init SQL lives under `chandler26-jdk17-learning-assistant/src/main/resources/db/init` and complete schemas under `chandler26-jdk17-learning-assistant/src/main/resources/db/schema`.
- Keep init SQL grouped by domain and easy to execute from a clean database.
- If the user has already executed an SQL file and a new schema change is needed, create a new SQL file instead of silently editing executed migration intent.
- Table and column comments should be clear enough for future maintenance.
- New-database initialization order and existing-database migration order must stay documented in `src/main/resources/db/README.md`.
- Any new unique constraint, status value, generated job table, or batch SQL must be reflected in both the current schema and the upgrade migration strategy.

## Verification

- Backend: run `mvn -q -DskipTests compile` at minimum after Java changes.
- Backend tests: run `mvn -q test`; after dependency changes also run `mvn -q dependency:analyze -DignoreNonCompile`.
- Mapper XML: parse every changed XML file before declaring the change complete.
- Frontend: run `node --check` on changed JS modules at minimum.
- For visual or interaction changes, verify through the browser or a concrete DOM/style check, including mobile-sensitive surfaces when relevant.
- For asynchronous or database-sensitive flows, verify the full state transition with a real database when available. If the environment has no database or model provider, state that limitation explicitly instead of claiming end-to-end verification.
- Before commit, run `git diff --check`, inspect `git diff --cached --stat`, and confirm the staged files are within the requested scope.
