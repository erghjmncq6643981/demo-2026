# Repository Collaboration Rules

## Scope

- Learning Assistant product: backend `chandler26-jdk17-learning-assistant`, frontend `chandler26-jdk17-learning-assistant-web`.
- FCC product: backend `chandler26-jdk21-fcc`, admin frontend `chandler26-fcc-admin-web`, and agent frontend `chandler26-fcc-client-web`.
- Treat the projects in each product as one delivery unit. Read the nested project `AGENTS.md` before changing code in that project.
- The FreeSWITCH Sidecar and operations console live in the sibling `cloud-2025` repository. Cross-repository FCC changes must inspect those contracts as well.

## Shared Rules

- Inspect first with `rg` / `rg --files`; follow local patterns and preserve unrelated dirty worktree changes.
- Use `apply_patch` for manual edits. Do not stage, commit, revert, or delete files outside the requested scope.
- Never hardcode real provider, database, SIP, NATS, FreeSWITCH, or model credentials. Logs, docs, SQL, and frontend bundles must not expose them.
- Product and design documents describe implemented and verified behavior only. Separate current behavior from known gaps; remove superseded proposals and duplicated descriptions.
- Important user actions require readable business logs and, where relevant, a persistent audit record.

## Cross-Project Delivery

- Inspect API, DTO, application service, mapper/SQL, and frontend adapter before changing a cross-layer feature.
- Frontends treat Snowflake and Java `Long` IDs as opaque strings. Never coerce them with `Number`, `parseInt`, arithmetic, or numeric sorting.
- Growable lists use pagination and compact summaries. Large definitions, traces, recordings, articles, cards, and similar payloads load from detail APIs.
- Schema changes need an upgrade migration strategy, baseline/init updates where applicable, and documented execution order.
- Java classes and methods require clear Javadoc. DTO/VO fields require Chinese `@Schema` descriptions. Enum values require a Chinese `desc`/`label` property and getter.
- State unavailable database, provider, FreeSWITCH, NATS, Redis, media, and browser verification honestly; never report unavailable end-to-end checks as passed.

## Product Boundaries

- Learning Assistant long-running AI work persists job state before execution, runs after commit, exposes item-level outcomes, and retries failed items only.
- FCC Java services control FreeSWITCH through NATS and the Sidecar; they do not open direct ESL connections.
- FCC call, channel/leg, control, node, bridge, command, event, flow, and business IDs retain distinct names and meanings across Java, JSON, SQL, logs, and TypeScript.
- FCC frontend business REST, business WebSocket, Sidecar operations HTTP, and SIP/WebRTC media channels are separate adapters. Do not hide one transport behind another or construct protocol commands in view components.

## Agent Organization

- Nested backend and frontend rules provide directory-specific constraints.
- `agents/` contains focused review playbooks. A specialist owns only its assigned review; the implementing agent remains responsible for integration and verification.
- Use `agents/frontend-engineering-governance.md` for frontend structure, oversized files, state ownership, and browser quality.
- Use `agents/fcc-realtime-reliability.md` for call control, NATS/Sidecar contracts, events, WebSocket, SIP/WebRTC, and recovery.
- Use `agents/repository-integrity.md` for documentation truth, generated artifacts, configuration duplication, secrets, and cross-repository scope.

## Delivery Checks

- Java changes: `mvn -q -DskipTests compile` and `mvn -q test` with the project-required JDK.
- JavaScript/TypeScript changes: run the project type/build check and `node --check` for changed JavaScript modules.
- Parse changed Mapper XML or run its validation test. Verify visual changes in a browser, including mobile-sensitive and long-text states.
- Before publishing: run `git diff --check`, inspect the complete changed-file scope, and confirm that generated output and unrelated files are excluded.
