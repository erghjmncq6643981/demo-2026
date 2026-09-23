# chandler26-fcc-client-web

FCC agent desktop built with Vue 3, TypeScript, Pinia, JsSIP, Tailwind CSS, and Vite.

A Windows Electron test shell is available. Build it with `npm run desktop:build` and verify its focused tests with `npm run desktop:test`. First launch asks for the hosted HTTPS workspace URL. The main process owns the authenticated business socket, tray notifications and received/shown/activated receipts. Signing, automatic updates and real Windows notification acceptance are not verified.

Customer records, phone binding, automatic outbound, callback scheduling and after-call summaries use fcc-server. Callback actions enqueue a durable progressive job; they do not separately originate in the browser. Summaries persist before completing ACW. See [cross-machine deployment and acceptance](../docs/fcc-cross-machine-acceptance.md).

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
- WebRTC and SIP-phone answer-mode selection; the `MOBILE` domain value is reserved but its call path is unavailable.
- Agent business WebSocket with heartbeat and reconnect.
- JsSIP registration, incoming/outbound session handling, remote audio, DTMF, mute, answer, and hangup.
- Outbound call, hold, transfer, and call-end handling. Supervisor actions are explicitly unavailable.
- Incoming screen pop, in-call workspace, ACW, callback queue, CDR list, and agent monitoring.

The client uses `/api/admin` for business data, `/api/telephony` for call commands, and `/ws/agent` for business events.

## Development

Prerequisites:

- Node.js compatible with Vite 6.
- `fcc-admin` on `8089`.
- `fcc-server` on `8085`.
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
- Focused tests cover selected configuration, lifecycle, control outcomes, DTMF single-path delivery and WebSocket identity boundaries. Real SIP registration, media failure and authenticated browser workflows remain unverified.
- Other agents' presence and endpoints are now shown as unknown; authoritative team presence is still unavailable.

See [the FCC frontend architecture](../docs/frontend-architecture-and-ui-design.md) for shared boundaries and acceptance rules.

See the [FCC documentation index](../docs/README.md), [product design](../docs/fcc-product-design.md), and [cross-machine deployment and acceptance](../docs/fcc-cross-machine-acceptance.md) for current boundaries and remaining work.
