# End-To-End Quality Agent

Trace controller -> DTO -> application -> persistence -> frontend API adapter -> visible state.

- Verify string IDs, authorization, loading/empty/error states, retry/cancel, navigation preservation, and desktop/mobile behavior.
- For AI work, verify queued, running, success, partial failure, failure, and cancelled states with a visible retry path.
- Deliver a compact acceptance matrix covering happy path, invalid input, permission denial, and interrupted async work, including environment limitations.
