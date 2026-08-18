# Learning Assistant Project Rules

## Scope

- Backend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant`
- Frontend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web`
- Treat both projects as one product. Changes in one side usually need API, DTO, SQL, or UI checks on the other side.

## General

- Inspect the existing code first and follow local patterns. Prefer `rg` / `rg --files` for discovery.
- Do not revert unrelated user changes. The worktree may be dirty.
- Use `apply_patch` for manual file edits.
- Keep implementation scoped to the requested behavior. Avoid opportunistic refactors.
- Never hardcode real API keys in source, docs, or SQL. Model API keys must be stored encrypted by the backend.

## Documentation

- Product and design documents must describe behavior that is implemented and verified in the current frontend and backend.
- Keep documentation concise and centered on product positioning, information architecture, core workflows, user-visible behavior, and important business rules.
- Remove stale proposals, session history, duplicated explanations, exhaustive code-derived field lists, and unimplemented roadmap items unless the user explicitly requests them.
- Maintain one clear source of truth for each topic. When behavior or naming changes, update the relevant document and remove the superseded description in the same change.
- Clearly distinguish preview/mock-only behavior from production behavior; never present mock data or an unfinished interaction as a completed backend capability.

## Frontend

- Keep feature code under `public/src/features/**`; shared utilities belong in `public/src/shared/**`.
- Do not add new large logic to `public/app.js`; use feature modules and a small facade/wiring layer.
- Do not open or close modals by directly calling `classList.remove('hidden')` / `classList.add('hidden')`. Use `showModal` / `hideModal` from `/src/shared/modal.js` so scroll position resets.
- Normalize wordbook ids through `/src/shared/wordbook.js` helpers before API calls or state comparisons.
- All destructive actions and navigation that discards context need a secondary confirmation dialog.
- Keep the navigation and fixed card controls from scrolling away. Scroll only the intended content panel.
- Check desktop and mobile behavior for any layout or interaction change, especially review cards, modals, wordbook lists, and long buttons.
- Use qwerty-learner as visual inspiration: focused learning surface, keyboard-friendly interactions, restrained product UI.

## Backend

- Use Spring Security + JWT for authenticated APIs.
- Throw `LearningAssistantException` or a project-specific runtime exception instead of generic runtime errors.
- System/runtime logs should be debug-level; business logs should be info-level and readable by business users, for example: `用户「小明」把单词「abandon」添加到单词本「默认单词本」`.
- Important user-facing operations should write both server logs and system log table records where appropriate.
- Avoid magic values. Put shared constants in `LearningConstants` only when the value has clear business meaning.
- Use MyBatis-Plus wrappers and existing mapper/service patterns.

## AI Agent And Vocabulary

- Learning vocabulary is one scene. A user should reuse one `ai_chat_session` per learning scene instead of creating a new session for every AI request.
- AI-generated vocabulary records are shared cache data. When adding to a wordbook, save a personal snapshot on the wordbook entry so later regeneration by another user does not overwrite personal learning details.
- Related words should contain semantic relations only: synonym, antonym, and word family. Collocations stay in the collocation area, not in related words.
- AI prompt templates must preserve required placeholders such as `{{term}}`; validate placeholders before saving.
- Vocabulary card JSON should include definitions, examples with Chinese translations, collocations with meanings, related words with part of speech, meaning, and phonetics, and memory tips.

## Database

- MySQL init SQL lives under `chandler26-jdk17-learning-assistant/src/main/resources/db/init`.
- Keep init SQL grouped by domain and easy to execute from a clean database.
- If the user has already executed an SQL file and a new schema change is needed, create a new SQL file instead of silently editing executed migration intent.
- Table and column comments should be clear enough for future maintenance.

## Verification

- Backend: run `mvn -q -DskipTests compile` at minimum after Java changes.
- Frontend: run `node --check` on changed JS modules at minimum.
- For visual or interaction changes, verify through the browser or a concrete DOM/style check, including mobile-sensitive surfaces when relevant.
