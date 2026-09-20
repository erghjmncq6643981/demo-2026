# FCC 呼叫流程与话单追踪现状

## 1. 文档范围

本文说明管理端当前可查询和展示的呼叫事实、流程信息与录音边界。早期文档中尚未实现的独立 Timeline API、冷热库自动切换、完整 HTTP 回调熔断链路和“全量 FreeSWITCH JSON 审查器”等描述不再作为已交付能力。

## 2. 数据来源

管理端统一通过 `fcc-admin :8089` 获取业务数据：

| 用途 | 当前接口 |
| --- | --- |
| CDR 分页 | `GET /api/admin/cdrs` |
| 今日聚合 | `GET /api/admin/cdrs/stats` |
| 单条详情 | `GET /api/admin/cdrs/{id}` |
| 录音元数据 | `GET /api/admin/recordings/{callId}/meta` |
| 录音播放 | `GET /api/admin/recordings/{callId}/stream` |
| 录音下载 | `GET /api/admin/recordings/{callId}/download` |
| 按录音 ID 播放/下载 | `/api/admin/recordings/by-rec-id/{recordingId}/*` |
| 回拨任务 | `GET /api/admin/callbacks` |

前端 `cdrApi.ts` 将列表、统计和详情保持为独立请求。列表只展示摘要，Leg 和执行轨迹以详情结果为准。

## 3. 身份和时间语义

- `id`：数据库记录 ID，前端按不透明字符串处理。
- `ctrlId`：一次呼叫控制关联标识。
- `bizId`：外部业务关联标识。
- `legChannelId`：FreeSWITCH Channel UUID。
- `recordingId`：录音事实 ID。

这些值不能互换，也不能在前端数值化。

通话时间来自后端持久事实：

- `initiatedAt`
- `answeredAt`
- `endedAt`
- `waitDurationMs`
- `talkDurationMs`
- `totalDurationMs`

前端只负责格式化，不重新推导计费或最终状态。

## 4. 当前详情展示

`CdrReportView.vue` 当前能够组合展示：

- 主叫、被叫、方向、坐席、分机和通话状态；
- 发起、接听、结束和时长；
- 挂机原因与满意度；
- 录音播放；
- Call Leg；
- 后端返回的 `executionTrace`；
- 关联回拨任务。

轨迹内容必须来自后端事实。前端静态示例或仿真日志不得混入真实话单详情。

## 5. 流程与路由事实

`fcc_call_session` 不再保存旧系统的 `routeMode`。详情使用以下字段表达彼此独立的事实：

- `modelType`：呼入、人工外呼、自动外呼等业务通话类型；
- `flowCode`：通话启动时绑定的流程代码；
- `routeTargetType` / `routeTargetId`：最终路由目标摘要；
- `fcc_route_attempt`：每次实际路由决策、候选坐席、结果与失败原因。

流程定义中的 `routeMode=IVR` 只是固定阶段编辑契约的判别字段，不是每通电话的路由策略枚举。话单详情显示实际 `flowCode` 或 `modelType`，不再把旧模式映射成看似真实的路由文案。完整运行证据至少需要：

1. 持久化的路由决策；
2. 对应 Call/Leg；
3. 命令和事件关联；
4. 失败/降级原因；
5. 可回溯的执行时间。

## 6. 录音边界

录音文件由 FreeSWITCH 写入共享目录，`fcc-server` 保存元数据，`fcc-admin` 提供读取。

录音指令执行时会先登记路径。Sidecar 与 Java 已统一为 `Event.Recording`，NATS 分类为 `record`，并补充契约测试源码；真实录音停止、落盘与播放尚未完成联调，不能仅根据已登记路径宣称录音完整落盘。

前端要求：

- 只使用授权 API，不拼接服务器文件路径；
- 区分无录音、录音处理中、文件缺失、无权限和读取失败；
- 不把录音 URL、电话或客户信息写入控制台日志；
- 播放/下载失败不能改变话单业务状态。

## 7. 回拨任务

未接待回拨是与源 Call 关联的工作流，不是第二套 CDR。当前管理端支持分页查询、指派和发起呼叫。重复指派、重复点击和并发状态变化必须以后端幂等与状态校验为准。

## 8. 当前工程债务

- `CdrReportView.vue` 已提取 `features/cdr/composables/useCdrReport.ts` 和 `features/cdr/model/callRecord.ts`；视图仍超过 600 行，列表与详情组件可继续拆分。
- CDR ID 按字符串透传；缺少响铃/录音时长时不再生成默认测量值。
- 纯模型测试覆盖缺失与零时长；详情状态、授权和录音失败路径仍缺少浏览器回归。
- 页面中的演示文案和实际后端事实需要继续隔离。

后续对该页面的实质功能改动必须先遵守项目 [AGENTS.md](../AGENTS.md) 的拆分和验证要求。
