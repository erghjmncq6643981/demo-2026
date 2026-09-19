# FCC 流程定义与 IVR 管理现状

更新：2026-09-19。本页描述当前实现，不把静态画布、通知受理或 JSON 语法检查当作运行成功。

## 当前边界

管理端通过 `/api/admin/flows` 查询流程，通过 `/{flowKey}/versions` 查询版本，通过 `/{flowKey}/draft` 保存草稿，通过 `/{flowKey}/publish` 发布草稿。定义来自后端 `definitionJson`，界面提供 JSON 编辑器，不再展示固定阶段、假发布差异或“100% 校验通过”。

`IvrFlowView.vue` 负责渲染；`features/flows/composables/useFlowEditor.ts` 拥有加载、选择、草稿、发布及反馈状态；`features/flows/model/flowDefinition.ts` 只验证 JSON 可解析且顶层为对象。API 适配继续由 `src/api/flowApi.ts` 负责。

## 版本与发布

后端使用 `fcc_flow_definition` 和 `fcc_flow_definition_version` 保存定义及版本快照。数据库 ID 按字符串处理，业务键 `flowKey` 不替代运行实例 ID。

1. 加载实际流程与版本，选择版本后显示对应定义。
2. 切换流程、版本或刷新前，对未保存编辑进行放弃确认。
3. 保存草稿后重新加载版本，显示“尚未发布”。
4. 只有已保存的 DRAFT 可发布，发布需要应用内确认；后端核对请求版本是否仍为当前草稿。
5. 数据库发布后刷新版本。Redis 通知和服务器 reload 属于后端通知尝试，页面明确提示运行时激活尚未确认。

这不能证明运行中的电话已经切换版本，也不能证明新呼叫已命中新版本。

## 未实现与验证边界

- 仿真入口禁用；`POST /api/admin/flows/simulate` 返回 `success=false`、引擎未接入、空目标和空轨迹。
- 编辑器只做 JSON 语法检查；服务端保存/发布/编译共用严格校验，目前仅支持 DID_DIRECT 与 didDirectConfig.workNo，拒绝未实现路由和未知字段。完整 IVR 动作与激活看板仍未实现。
- 定义中不得保存 SIP、NATS、数据库或第三方凭据。
- 纯模型测试覆盖合法对象、非法 JSON 和非对象边界；构建通过。登录后的草稿/发布浏览器回归及真实 reload/呼叫联调未完成。

后续扩展遵守 [AGENTS.md](../AGENTS.md)，不得恢复静态流程节点冒充后端定义。
