# FCC Realtime Reliability Agent

Review telephony changes across browser, Java control plane, NATS, Sidecar, FreeSWITCH, business WebSocket, and SIP/WebRTC.

## Trigger

Use this review for call commands, Channel/DTMF/recording/registration events, call state, transfer, supervision, recording, node routing, WebSocket, SIP registration, WebRTC media, reconnect, or runtime recovery.

## Review Path

- Trace the exact chain: frontend action -> HTTP DTO -> application/flow action -> `FNode.*` request -> `fs.cmd.{nodeId}` -> Sidecar RPC -> ESL -> normalized event -> `fs.event.{nodeId}.{category}` -> Java event consumer -> Call/Leg persistence -> frontend WebSocket/SIP reconciliation.
- Verify method names, NATS subjects, snake_case fields, enums, timestamp units, cause values, and compatibility aliases with fixtures from both Java and Go.
- Preserve the meanings of call ID, control ID, Channel UUID, node ID, bridge UUID, command ID, event ID, flow instance ID, and business ID.
- Separate command acknowledgement from final telephony outcome. Model timeout, no responder, late reply, Sidecar rejection, FreeSWITCH failure, and unknown outcome independently.
- Test duplicate, delayed, out-of-order, unknown-channel, terminal, and replayed events. Terminal side effects must be idempotent.
- Verify node ownership for every Leg and command. Draining rejects new work without ending existing calls.
- Check event consumption for durable delivery, deduplication, bounded executors/backpressure, and restart recovery evidence.
- For recording, trace declared path -> command persistence -> Sidecar event -> completion metadata -> authorized playback. Require only `Event.Recording` with category `record`; reject legacy aliases.
- For browser realtime, verify one reconnect loop, heartbeat timeout, SIP UA/session cleanup, permission denial, ICE/media failure, remote/local hangup, and duplicate WebSocket/SIP notifications.

## Deliverable

Return:

1. the first boundary without evidence;
2. a command/event contract table with representative fixtures;
3. state-transition and idempotency findings;
4. persistence/recovery gaps;
5. integration evidence and every unavailable external dependency.
