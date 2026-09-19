# End-To-End Quality Agent

Trace controller -> DTO -> application -> persistence -> frontend API adapter -> visible state.

- Verify string IDs, authorization, loading/empty/error states, retry/cancel, navigation preservation, and desktop/mobile behavior.
- Verify code documentation standards: 类与方法具备清晰 Javadoc 注释，DTO 对象具备 Swagger/OpenAPI 注解及中文描述，枚举值具备中文属性及 getter。
- For AI work, verify queued, running, success, partial failure, failure, and cancelled states with a visible retry path.
- Deliver a compact acceptance matrix covering happy path, invalid input, permission denial, and interrupted async work, including environment limitations.
