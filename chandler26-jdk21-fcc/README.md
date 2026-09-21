# chandler26-jdk21-fcc

Current FCC control-plane backend for the admin and agent frontends. It is a Java 21, Spring Boot 4.1.1 modular monolith that controls FreeSWITCH through NATS and the Go Sidecar.

The earlier statement that this repository contained only a skeleton is no longer accurate. The source now includes administration APIs, call-control APIs, persistence, authentication, WebSocket delivery, flow handling, recording access, and integration tests.

## Modules

| Module | Responsibility |
| --- | --- |
| `fcc-common` | shared FCC contracts, command/event DTOs, enums, entities, and utilities |
| `fcc-server` | executable runtime service (default port `8085`): published-flow execution, call control, customer/outbound facts and scheduling, NATS commands/events, recording metadata, and agent WebSocket |
| `fcc-server-starter` | client SDK boundary for future remote server contracts; currently empty because there is no Feign consumer |
| `fcc-admin` | executable administration service (default port `8089`): authentication plus maintenance/query APIs for agents/groups/endpoints, extensions, CDR/recordings, callbacks, flows, customers, outbound jobs, resources, configuration, and fleet |
| `fcc-admin-starter` | client SDK boundary for future remote admin contracts; currently empty because there is no Feign consumer |

Runtime bootstrap, configuration, and tests live with their executable service. The starter modules must not depend on service implementations; when an actual remote consumer appears, they may expose only the required client contract.

## Runtime architecture

```text
fcc-admin-web :8000 --> fcc-admin :8089 ----> MySQL / Redis
                                  |
                                  +---------> Sidecar :8088 (selected admin operations)

fcc-client-web :8888 --> fcc-admin :8089
                       +> fcc-server :8085 --> NATS --> Sidecar --> FreeSWITCH
                       +> /ws/agent :8085
                       +> FreeSWITCH SIP WebSocket for media
```

Java does not connect to ESL directly.

The control/runtime split is: maintain and inspect through `fcc-admin`; consume and execute through `fcc-server`. Flow definitions are drafted, validated, and published in admin, then pinned and executed by server. Customer and automatic-outbound screens also live in admin, while their facts, scheduler, attempts, and telephony side effects remain in server. The agent client does not expose either management workspace.

## Implemented contracts

### fcc-server

- `POST /api/telephony/call/outbound`
- `POST /api/telephony/call/hangup`
- `POST /api/telephony/call/hold`
- `POST /api/telephony/call/dtmf`
- `POST /api/telephony/call/supervise`
- `POST /api/telephony/call/transfer`
- `POST /api/telephony/call/flow/reload`
- `WS /ws/agent` with authenticated subprotocol headers; query parameters do not establish identity.

The server sends logical `FNode.*` commands to `fs.cmd.dispatch`, subscribes to `fs.event.>`, drives flow actions, persists call/session facts, and pushes screen-pop/call events to connected agents. Node ownership is resolved below the FCC business boundary.

Recording commands persist the declared file path. Sidecar and Java now use only `Event.Recording` on `fs.event.{nodeId}.record`. Contract tests exist on both sides; live recording completion still requires FreeSWITCH/NATS verification.

`FlowActionType` is the shared action catalog for admin validation and server execution. Every `FNodeMethod` has an object-backed action entry; internal actions are reserved for cohesive FCC business closures, while configured third-party actions use the versioned `ThirdPartyFlowRequest`/`ThirdPartyFlowResponse` contract and never accept a URL from the flow model.

### fcc-admin

The `/api/admin` surface includes:

- authentication and current user;
- administrator and agent accounts;
- groups, members, endpoint bindings, and substitutions;
- extensions and IVR binding;
- paginated CDR, statistics, detail, recordings, and callback tasks;
- paginated flow summaries and version summaries, on-demand version details, drafts, and publication;
- customer maintenance plus automatic-outbound task creation, control, and result inspection, delegated to fcc-server after a second online permission check;
- telephony resources, system configuration, and client fleet data.

## Storage

- MySQL is the source of truth for FCC business facts and configuration.
- Redis accelerates reconstructable runtime state such as extension presence and publication notifications.
- NATS Core provides request/reply and current event delivery.
- FreeSWITCH media files are shared through the configured recording base directory.
- `docs/fcc-schema.sql` is the baseline schema. Not every baseline table has a complete runtime workflow yet; mapper/service coverage is the implementation boundary.

Active calls also use an in-memory session index for event correlation. MySQL remains necessary for durable facts; recovery behavior must not be inferred solely from the in-memory map.

The shared `FccIdentifierJacksonModule` is registered in admin/server and serializes Long bean properties named `id` or ending in `Id` as strings. Numeric measurements stay numeric. Its unit test passes under the temporary JDK 21; Map values, other names and actual Spring HTTP integration still require verification.

## Configuration

Both executable services use environment-driven configuration. Important variables include:

- `SERVER_PORT`
- `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `NATS_URL`
- `SIDECAR_ADMIN_URL`
- `FCC_RECORDING_BASE_DIR`
- `FCC_ADMIN_BASE_URL`, `FCC_SERVER_BASE_URL`, `FCC_FLOW_RELOAD_TOKEN`, `FCC_WS_ALLOWED_ORIGINS`
- `FCC_SIP_WS_URL`, `FCC_SIP_DOMAIN`, `FCC_SIP_ENCRYPTION_KEY` (admin only)

`MYSQL_PASSWORD` has no source-code default and must be supplied by the deployment environment.

### First administrator

A fresh database has no default account. Create the first administrator once by starting `fcc-admin` with:

```text
FCC_BOOTSTRAP_ADMIN_ENABLED=true
FCC_BOOTSTRAP_ADMIN_USERNAME=<operator-chosen username>
FCC_BOOTSTRAP_ADMIN_REAL_NAME=<display name>
FCC_BOOTSTRAP_ADMIN_PASSWORD=<8-64 character secret>
```

The service stores only a PBKDF2 hash. It never logs or returns the password. Bootstrap does nothing when disabled, is idempotent only for the same already-created administrator, and refuses to create or overwrite an account when any other console account exists. Remove all `FCC_BOOTSTRAP_ADMIN_*` variables and restart immediately after the first successful login.

The Java node ID must exactly match the Sidecar `NODE_ID`. Database, Redis, NATS, Sidecar, SIP, and recording credentials/paths must be supplied by deployment configuration rather than committed defaults.

## Build and test

Prerequisites:

- JDK 21
- Maven 3.6.3+
- MySQL 8 and Redis 7 for environment-dependent integration tests
- NATS and Sidecar/FreeSWITCH for telephony integration

```bash
mvn -q -DskipTests compile
mvn -q test
```

Test sources cover utilities, WebSocket behavior, database connectivity, telephony flow, agents, resources, CDR, callback, extension, and flow-definition paths. Their presence does not prove that external dependencies were available in a particular run.

## Documentation

- [Architecture and current boundaries](./docs/DESIGN.md)
- [Product design and completion priorities](../docs/fcc-product-design.md)
- [Frontend/backend contract findings](../docs/fcc-contract-alignment.md)
- [Contract remediation and deployment order](../docs/fcc-contract-remediation.md)
- [Baseline schema](./docs/fcc-schema.sql)
- [Project rules](./AGENTS.md)
- [Cross-project frontend architecture](../docs/frontend-architecture-and-ui-design.md)
- [Testing status and acceptance](../docs/testing-architecture-and-test-cases.md)
