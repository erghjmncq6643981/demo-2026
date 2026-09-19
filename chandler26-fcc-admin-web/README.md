# chandler26-fcc-admin-web

FCC operations and business administration frontend built with Vue 3, TypeScript, Pinia, Element Plus, Tailwind CSS, and Vite.

## Runtime relationship

```text
Browser :8000
    |
    | /api/admin
    v
fcc-admin-starter :8089
    |             |
    v             v
 MySQL          Redis

fcc-admin may call the Sidecar :8088 for extension and telephony-resource operations.
```

This frontend does not call NATS, ESL, or FreeSWITCH directly.

## Implemented modules

- Administrator login and current-user session.
- Call records, aggregate statistics, call details, recordings, and callback tasks.
- Agent accounts, endpoint bindings, groups, membership, and organization reporting.
- Extension management and IVR binding.
- Flow list, version history, draft save and publish. The simulation endpoint explicitly reports that its engine is unavailable.
- Runtime/operations overview and client/resource management surfaces.

The source of truth is the `/api/admin` contract implemented by `chandler26-jdk21-fcc/fcc-admin`. Large call/flow payloads should be loaded from detail APIs rather than copied into list responses.

## Development

Prerequisites:

- Node.js compatible with Vite 6; model tests require Node.js 22.18+ with native TypeScript stripping.
- `fcc-admin-starter` running on port `8089`.
- MySQL and Redis configured for the backend.

```bash
npm ci
npm run dev
npm run build
node --test tests/governance.test.mjs
```

Development URL: `http://localhost:8000`.

Authentication uses the backend-defined `satoken` header. Browser state is not an authorization boundary; all permissions must also be enforced by the backend.

## Engineering baseline

Project-specific rules are in [AGENTS.md](./AGENTS.md). Feature domains must own their API adapter, state/composable, UI, and tests. `App.vue` and the layout remain composition only.

Known debt:

- Extension management composes extensions, settings and fleet features. Flow, CDR and group state now belongs to feature composables; flow JSON, CDR mapping and group trees have separate pure models. CDR and group rendering still exceed 600 lines and require further component review before growth.
- Updated administration API IDs use opaque strings; new adapters must preserve this contract.
- Generated Vite configuration output and TypeScript build metadata have been removed from tracked sources and are ignored.
- Four model tests cover flow JSON boundaries, group ordering/string IDs/cycles and absent/zero CDR measurements. Permission, pagination and publication interactions still need browser regression coverage.
- Existing product copy and some screens still contain prototype data or optimistic claims. Live data, simulated data, and unavailable metrics must be visibly distinct.

See [the FCC frontend architecture](../docs/frontend-architecture-and-ui-design.md) for shared boundaries and acceptance rules.
