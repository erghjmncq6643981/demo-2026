# FCC Admin Frontend Rules

The parent `AGENTS.md` also applies.

## Product Boundary

- This project is the operations and administration frontend. It consumes `fcc-admin` through `/api/admin`; it does not call NATS, ESL, or FreeSWITCH directly.
- The backend remains the source of truth for authentication, authorization, call facts, flow versions, agent/group membership, recordings, and callback tasks.
- Hidden controls are not authorization. Every privileged operation must be enforced by the backend and represented by a readable success or failure state in the UI.

## Structure And Data

- New product code belongs in `src/features/**`; generic UI, transport, formatting, and feedback utilities belong in `src/shared/**`.
- Feature domains are `identity`, `agents`, `groups`, `flows`, `cdr`, `callbacks`, `extensions`, `monitoring`, and `reporting`.
- A feature owns its pages/components, API adapter, state/composables, pure models, and tests. Cross-feature workflows belong in a named coordinator. Keep `App.vue` and `AdminLayout.vue` limited to shell and composition.
- Do not add more behavior to a production file above 1000 lines. A substantial change to an existing oversized file must first split it by view section, state/composable, API mapping, and interaction flow.
- API envelope handling, authentication headers, and error normalization stay in the transport layer. Views must not interpret multiple response shapes.
- Treat every backend ID as an opaque string, including fields currently typed as `number`. Never use numeric coercion or arithmetic for identity values.
- Lists are paginated summaries. Load call legs, execution traces, flow JSON, recording metadata, and other large payloads from detail endpoints on demand.
- Do not commit generated Vite config output, declaration output, `*.tsbuildinfo`, `dist`, coverage output, or local recordings.

## Interaction And State

- Every data surface covers loading, empty, error, retry, unauthorized, and stale-data states. Mutations additionally expose pending, success, validation failure, and server failure.
- Destructive or service-affecting actions such as agent deletion, extension deletion, flow publication, forced hangup, and resource changes require explicit confirmation and cannot rely on browser-native `alert`/`confirm` in new code.
- Preserve filters, pagination, selected tabs, and scroll context when opening and closing details.
- Operational screens are dense, quiet, and scan-oriented. Avoid decorative dashboard cards, oversized headings, fake live metrics, and placeholder records presented as production data.
- Long labels and identifiers must wrap or truncate with an accessible full-value affordance; controls must remain usable at desktop and mobile-sensitive widths.

## Security And Reliability

- Do not place tokens, passwords, SIP secrets, recording signed URLs, or personal data in logs, fixtures, screenshots, or persistent browser storage beyond the established authentication contract.
- Recording playback and download always use authorized backend endpoints. Never expose server filesystem paths.
- Flow publication UI must distinguish saved draft, published version, runtime reload notification, and verified runtime activation; do not collapse them into one success claim.

## Verification

- Run `npm run build` after TypeScript, Vue, import, or configuration changes.
- Add focused tests when extracting pure flow, CDR, permission, pagination, or response-mapping logic. Do not grow another complex untested view module.
- Before delivery scan for ID coercion:
  `rg -n -i "(Number|parseInt|parseFloat)\\([^\\n]*(id|identifier)|\\b(id|[A-Za-z]+Id)\\s*:\\s*(number|Number|parseInt)" src`
- Verify changed workflows in a browser at desktop and mobile-sensitive widths. Include authentication expiry, backend failure, empty results, long text, and destructive confirmation where relevant.

## Focused Review

- Use `../agents/frontend-engineering-governance.md` when changing a view, feature boundary, store/composable, API adapter, layout, or oversized file.
- Use `../agents/fcc-realtime-reliability.md` when management behavior affects live calls, flow reload, recording state, callbacks, or Sidecar operations.
- Use `../agents/security-and-authorization.md` for authentication, permissions, recordings, phone data, and destructive administration.
- Use `../agents/repository-integrity.md` for docs, generated files, build configuration, fixtures, and broad cleanup.
