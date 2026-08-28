# Learning Assistant Collaboration Rules

## Scope

- Backend: `chandler26-jdk17-learning-assistant`; frontend: `chandler26-jdk17-learning-assistant-web`.
- Treat both projects as one product. Read the nested project `AGENTS.md` before changing code in that project.

## Shared Rules

- Inspect first with `rg` / `rg --files`; follow local patterns. Preserve unrelated dirty worktree changes.
- Use `apply_patch` for manual edits. Do not stage, commit, revert, or delete files outside the requested scope.
- Never hardcode real model keys. Backend stores them encrypted; logs, docs, SQL, and frontend must not expose them.
- Product/design documents describe implemented and verified behavior only. Remove superseded proposals and duplicated descriptions.
- Important user actions require readable business logs and, where relevant, a system-log record.

## Cross-Project Delivery

- Inspect API, DTO, application service, mapper/SQL, and frontend module before changing a cross-layer feature.
- Frontend treats Snowflake/`Long` IDs as opaque strings; backend serializes them through shared Jackson configuration.
- Lists and calendars return compact summaries. Article, card, assessment, note, and other large data load from a single-object detail API.
- Schema changes need an upgrade migration strategy, init/schema updates where applicable, and documented execution order.
- Long-running AI work persists job state before execution, runs after commit, exposes item-level outcomes, and retries failed items only.
- State unavailable database or provider verification honestly; never report unavailable end-to-end checks as passed.

## Agent Organization

- Backend and frontend nested rules provide implementation constraints by directory.
- `agents/` contains specialist review/delegation playbooks for AI reliability, learning product quality, data performance, end-to-end quality, and authorization.
- A specialist owns only its assigned review. The implementing agent remains responsible for integration and verification.

## Delivery Checks

- Java changes: `mvn -q -DskipTests compile` and `mvn -q test`.
- JavaScript changes: `node --check` on changed modules; run the module check after structural changes.
- Parse changed Mapper XML or run its validation test. Verify visual changes in browser, including mobile-sensitive flows.
- Before publishing: `git diff --check`, staged-stat inspection, and staged-scope confirmation.
