# FCC Agent Frontend Rules

The parent `AGENTS.md` also applies.

## Product Boundary

- This project is the agent desktop. It uses `/api/admin` for identity and business data, `/api/telephony` plus `/ws/agent` for call control/events, and SIP over WebSocket for WebRTC media.
- REST control, business WebSocket, and SIP/WebRTC are separate transports with separate lifecycle and failure states. Do not merge them into one generic connection flag.
- The frontend never calls NATS or ESL and never invents a successful call state before the authoritative transport confirms it.

## Structure And Data

- New product code belongs in `src/features/**`; generic transport, UI, formatting, and feedback utilities belong in `src/shared/**`.
- Feature domains are `identity`, `endpoint`, `call`, `media`, `realtime`, `records`, `callbacks`, and `supervision`.
- A feature owns its components, API adapter, store/composable, models, and tests. Cross-feature call orchestration belongs in an explicit coordinator. Keep `App.vue` limited to shell, lifecycle wiring, and top-level navigation.
- View components do not call backend APIs directly for new workflows. They dispatch a feature action/use case that owns pending state, retry, error mapping, and reconciliation.
- At 600 production lines perform a design review; at 1000 lines splitting is mandatory. Split by rendering, call-state transitions, media lifecycle, API access, and event handling.
- Treat `callId`, `ctrlId`, channel UUIDs, recording IDs, agent IDs, and all backend `Long` values as opaque strings.
- Do not commit generated Vite config output, declaration output, `*.tsbuildinfo`, `dist`, coverage output, or local recordings.

## Call And Realtime State

- Model call state transitions centrally and monotonically. Duplicate, delayed, or out-of-order WebSocket/SIP events must not move a terminal call backwards or trigger duplicate hangup, ACW, recording, or notification actions.
- A REST command acknowledgement is not the final telephony outcome. Reconcile commands with WebSocket and SIP events and expose timeout/unknown states instead of assuming success.
- WebSocket reconnect uses bounded backoff, one active reconnect timer, heartbeat timeout detection, and full listener/timer cleanup on logout or unmount.
- SIP UA/session creation and teardown are idempotent. Microphone permission denial, registration failure, ICE/media failure, remote hangup, local hangup, and browser autoplay blocking require distinct visible states.
- Endpoint changes cannot strand an active call. Disable, defer, or explicitly coordinate the switch while a call is ringing or connected.
- Hostnames, ports, SIP domains, WebSocket schemes, credentials, and ICE servers come from validated runtime configuration. Do not add hardcoded LAN addresses, demo passwords, or unconditional `ws://` URLs.

## Desktop Packaging And Deployment (桌面客户端打包与部署规则)

- **域名写到安装包规则（强绑定机制）**：构建桌面客户端安装包前，必须核对并将目标工作台服务地址/域名写入安装包预置配置文件（`desktop/deployment.json`），直接打入安装包内（如当前测试包统一使用 `http://fcc.local:8888`，由内网/虚拟机 DNS 或 hosts 映射至 FCC 主机）。
- **登录者严禁配置服务地址**：客户端启动与运行期间，直接加载安装包内置的工作台地址，严禁让最终坐席或登录者在 UI 界面手动输入、修改或自适应猜测后端服务地址，避免因配置不一致或操作失误导致请求与登录失败。
- **环境更换严格重新打包**：若工作台域名、IP 或协议发生变更，必须在构建前更新安装包预置配置（`desktop/deployment.json`）并重新打包，不得依赖客户端运行期动态输入或本地缓存机制。
- **运行期通信一致性**：运行期 API 基础地址（`adminApi`、`telephonyApi`）、WebSocket 连接（`/ws/agent`）以及 SIP/WebRTC 传输，必须基于当前受信任的工作台域名保持严格一致，不得绕过预置部署配置引入散落的硬编码或冲突的本地配置。

## Interaction

- Cover logged-out, connecting, ready, ringing, dialing, connected, held, reconnecting, failed, ACW, and idle states without overlapping controls.
- Important controls remain reachable during a call. Destructive actions such as reject, hangup, supervisor kill, and abandoning ACW require appropriate confirmation or a deliberate single-action design.
- Use restrained, work-focused visuals. Live indicators must represent real measurements; label simulated or unavailable data explicitly.
- Preserve record filters and current work context when a screen pop or call workspace opens and closes.

## Verification

- Run `npm run build` after TypeScript, Vue, import, or configuration changes.
- Add focused tests for every changed call-state transition, duplicate/out-of-order event case, reconnect path, timer cleanup, and API mapping. Media-device behavior still requires browser verification.
- Before delivery scan for ID coercion:
  `rg -n -i "(Number|parseInt|parseFloat)\\([^\\n]*(id|identifier)|\\b(id|[A-Za-z]+Id)\\s*:\\s*(number|Number|parseInt)" src`
- Verify in a browser: login expiry, WebSocket disconnect/reconnect, SIP registration failure, permission denial, incoming and outbound calls, remote/local hangup, ACW, long text, and mobile-sensitive layout.

## Focused Review

- Use `../agents/frontend-engineering-governance.md` for feature ownership, component/store boundaries, file size, UI state, and browser quality.
- Use `../agents/fcc-realtime-reliability.md` for every call-state, WebSocket, SIP/WebRTC, recording, transfer, supervision, reconnect, or media change.
- Use `../agents/security-and-authorization.md` for login, agent identity, supervisor actions, recordings, phone data, and token handling.
- Use `../agents/repository-integrity.md` for docs, generated files, build configuration, fixtures, and broad cleanup.
