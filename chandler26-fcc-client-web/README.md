# chandler26-fcc-client-web

FCC agent desktop built with Vue 3, TypeScript, Pinia, JsSIP, Tailwind CSS, and Vite.

## Runtime relationship

```text
                              +--> fcc-admin :8089
                              |    identity, endpoints, CDR, callbacks
Browser :8888 -- REST -------+
       |
       +-- REST / WebSocket ------> fcc-server :8085
       |                            call control and screen-pop events
       |
       +-- SIP over WebSocket ----> FreeSWITCH :5066/:7443
                                    WebRTC signaling and media
```

The three channels have independent lifecycle and failure states. A successful REST response is only command acknowledgement; final call state comes from telephony events and SIP session state.

## Implemented workflows

- Agent login and endpoint loading/switching.
- WebRTC, SIP phone, and mobile answer-mode selection.
- Agent business WebSocket with heartbeat and reconnect.
- JsSIP registration, incoming/outbound session handling, remote audio, DTMF, mute, answer, and hangup.
- Outbound call, hold, transfer, supervisor actions, and call-end handling.
- Incoming screen pop, in-call workspace, ACW, callback queue, CDR list, and agent monitoring.

The client uses `/api/admin` for business data, `/api/telephony` for call commands, and `/ws/agent` for business events.

## Development

Prerequisites:

- Node.js compatible with Vite 6.
- `fcc-admin-starter` on `8089`.
- `fcc-server-starter` on `8085`.
- A browser-reachable FreeSWITCH SIP WebSocket endpoint and valid extension credentials.

```bash
npm ci
npm run dev
npm run build
```

Development URL: `http://localhost:8888`.

## Engineering baseline

Project-specific rules are in [AGENTS.md](./AGENTS.md). Call state, business WebSocket state, and SIP/media state must remain separate and be reconciled by an explicit coordinator.

Known debt:

- Business WebSocket and ICE configuration lives in `src/shared/config/runtimeConfig.ts`. SIP address/domain and credentials now come from authenticated `/api/admin/auth/sip-config`, remain in memory and are never embedded as a Vite password. The business WebSocket authenticates using subprotocol headers and defaults to the page origin with HTTPS/WSS.
- Hold and transfer report accepted requests, not final media outcomes; failures preserve the active view. Supervisor actions are explicitly unavailable. Real media confirmation still requires integration checks.
- Some components call API functions directly instead of dispatching a feature use case/store action.
- Generated Vite/declaration/build-info files have been removed from tracked sources and ignored.
- Ten tests cover selected configuration, lifecycle, control outcomes, DTMF single-path delivery and WebSocket identity boundaries. Real SIP registration, media failure and authenticated browser workflows remain unverified.
- Other agents' presence and endpoints are now shown as unknown; authoritative team presence is still unavailable.

See [the FCC frontend architecture](../docs/frontend-architecture-and-ui-design.md) for shared boundaries and acceptance rules.

See [contract findings](../docs/fcc-contract-alignment.md) and [product priorities](../docs/fcc-product-design.md) for remaining functional gaps.

Deployment and credential format changes are documented in [the remediation record](../docs/fcc-contract-remediation.md).
