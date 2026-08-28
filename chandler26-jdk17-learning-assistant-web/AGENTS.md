# Frontend Rules

The parent `AGENTS.md` also applies.

## Structure And Data

- Product code belongs in `public/src/features/**`; generic utilities in `public/src/shared/**`.
- Domains are `identity`, `vocabulary`, `learning`, `reading`, `ai`, `task`, and `system`. In `ai`, separate agent, model/provider, session, and prompt modules.
- A domain owns its UI, API adapter, state, and pure models. Cross-domain composition belongs in a coordinator; keep `public/app.js` as small wiring only.
- At 1000 production lines, perform mandatory design review and split by rendering, API access, state transitions, interaction orchestration, or async listener. Document any temporary exception.
- Normalize wordbook IDs with `/src/shared/wordbook.js`. Treat every backend ID as an opaque string: never `Number`, `parseInt`, arithmetic, or numeric ID sorting.
- Use compact list/calendar data and load full material only on start/review. Keep async task status, failure, retry, and cancellation visible.

## Interaction

- Use `showModal` / `hideModal` from `/src/shared/modal.js`, never direct hidden-class toggling.
- Destructive actions and context-discarding navigation require secondary confirmation.
- Keep navigation and fixed card controls visible; scroll only the intended content panel.
- Maintain the flow: wordbook/import -> plan -> material -> challenge -> progress/review -> note/history. Cover loading, empty, partial-failure, retry, permission, and stale-data states.
- Use focused, keyboard-friendly, restrained learning UI inspired by qwerty-learner. Check desktop and mobile-sensitive surfaces.

## Verification

- Run `node --check` on changed modules and `npm run check` after module moves/import rewiring.
- Before commit scan for ID coercion:
  `rg -n -i "(Number|parseInt|parseFloat)\\([^\\n]*(id|identifier)|\\b(id|[A-Za-z]+Id)\\s*:\\s*(Number|parseInt)" public/src`
- Verify visual/interaction changes in browser.
