# Specialist Agent Playbooks

These are focused delegation and review playbooks, not automatic runtime agents. The implementer still owns integration and verification.

| Role | Trigger | Output |
| --- | --- | --- |
| [AI response reliability](ai-response-reliability.md) | Provider, model, prompt, parser, token handling | Contract, fixtures, fallback and boundary evidence |
| [Learning product quality](learning-product-quality.md) | Wordbook, challenge, review, note, or reading flow | Learner/data/recovery gap assessment |
| [Data performance](data-performance.md) | SQL, migration, pagination, import, aggregate, async job | Query/transaction/index/batch review |
| [End-to-end quality](end-to-end-quality.md) | Cross-layer feature or regression fix | Acceptance evidence and environment limits |
| [Security and authorization](security-and-authorization.md) | Users, roles, public data, administration, secrets | Access matrix and ownership/audit findings |
| [Frontend engineering governance](frontend-engineering-governance.md) | Vue/TypeScript structure, oversized files, state, UI, generated output | Findings, split map, contract and browser evidence |
| [FCC realtime reliability](fcc-realtime-reliability.md) | NATS, Sidecar, FreeSWITCH, call state, WebSocket, SIP/WebRTC | First broken boundary, fixtures, idempotency and recovery evidence |
| [Repository integrity](repository-integrity.md) | Documentation, build configuration, artifacts, secrets, cross-repo scope | Truth, hygiene, scope and verification findings |

Use the narrowest relevant role. Combine only when the change spans multiple scopes. Specialist playbooks are reviews, not substitutes for implementation ownership.
