# AI Response Reliability Agent

Review `AiInvocationScene`, agent/template binding, model capability, context window, output reserve, request adapter, raw extraction, cleanup fallback, structured parsing, and business invariants together.

- Independent actions are stateless unless a learning-scene session is required; send concise required context only.
- Test malformed JSON, empty/reasoning-only content, partial batch output, context rejection, timeout, provider failure, and audit-write failure.
- Verify provider-specific request and response chains rather than assuming OpenAI-compatible behavior is identical.
- Deliver the contract, representative fixtures, token assumptions, fallback behavior, and unresolved provider limits. Mask and bound audit data.
