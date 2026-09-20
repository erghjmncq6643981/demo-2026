# FCC 契约问题处理与部署说明

日期：2026-09-19。此次处理用户确认的七项高优先级差异，未实现功能明确不可用，不引入旧协议兼容。

## 已落实的行为

| 原问题 | 当前行为 | 仍需联调的边界 |
| --- | --- | --- |
| 转接假成功并退出 | 必须提供本人 callId；节点错误/ESL -ERR/超时返回失败或未知；只返回 ACCEPTED，不推假挂机，不主动拆原坐席，前端保留工作区 | 转接后的真实 Leg/Bridge、原坐席退出与失败恢复 |
| 保持假成功 | 不返回请求值冒充 isHeld；页面显示“请求保持/请求恢复”和待确认提示；失败保留原请求状态 | 当前没有保持完成事件，页面不宣称媒体已保持 |
| 班长假成功 | SPY/COACH/BARGE/KILL 均返回 HTTP 501；前端入口禁用，他人状态显示未知，移除固定工号兜底 | 真实班长媒体能力仍未实现 |
| 缺少 callId 控制最近通话 | 控制服务拒绝空 callId、未知会话、跨坐席及未知节点；挂机等待真实结束事件再清理 | 重启恢复、跨节点复杂转接仍需完善 |
| REST/WS 身份脱节 | 两类 REST 均携带 satoken；控制服务在线查询管理端身份；WS 从认证子协议头取得令牌，忽略 URL 工号，收发前重新校验；Origin 默认同源 | 认证服务故障时拒绝话务操作；在线核验增加延迟，后续可改为受控短期票据 |
| SIP 共享构建密码 | `/api/admin/auth/sip-config` 仅返回本人唯一 ENABLED WebRTC 分机配置，no-store；前端仅内存使用；新写入口令 AES-256-GCM 加密，拒绝旧明文 | 凭据轮换管理和开通失败自动补偿仍待完善；浏览器必须能接触本人的注册凭据，不能当作不可提取秘密 |
| 发布与激活不同步 | 保存/发布/编译共用定义校验，flow:write 权限；通知在 afterCommit 执行；reload 需要服务间令牌；编译失败返回失败并保留旧有效节点；异步应答检查并记录结果 | 通知仍非持久 Outbox，没有跨实例激活看板或自动补偿；数据库发布成功不等于所有实例已激活 |

额外处理：DTMF 改为控制面单一发送路径；移除 Channel 事实中的固定节点，节点取真实事件并检查归属；移除外呼用 callId 冒充 Channel 的绑定；修复已发现的 Lambda 编译问题及依赖伪造 CDR 轨迹的过时测试。

## 当前流程定义契约

管理端画布维护固定阶段 IVR 的可编辑参数；服务端当前只接受下面的结构（工号仅为示例，不导入数据库）：

```json
{
  "routeMode": "IVR",
  "template": "INBOUND",
  "menu": {
    "enabled": true,
    "prompt": "/opt/freeswitch/sounds/fcc/welcome.wav",
    "timeoutSeconds": 10
  },
  "branches": [
    {
      "digit": "1",
      "targetType": "GROUP",
      "target": "sales",
      "queueSeconds": 60
    }
  ],
  "defaultRoute": {
    "targetType": "AGENT",
    "target": "your-agent-work-number",
    "queueSeconds": 60
  },
  "timeoutAction": "CALLBACK"
}
```

固定阶段为 `ENTRY -> MENU -> BRANCH -> ROUTE -> BRIDGE -> CONNECTED -> END`，动作和顺序不能由管理端改变。服务端校验菜单、媒体绝对路径、单键分支、坐席/技能组目标、排队时限和结束动作；旧路由模型、未知字段、脚本、Java 类名和动态 URL 均被拒绝。呼入、外呼、通知外呼和话机绑定的完整系统模型由 `system-models.json` 生成并随全新数据库基线发布；运行端应用服务执行对应动作并记录阶段事实。发布结果中的 `PENDING` 仍表示运行端激活待确认，不代表真实 FreeSWITCH 链路已完成。

## 必需部署配置

| 配置 | 设置位置 | 要求 |
| --- | --- | --- |
| FCC_ADMIN_BASE_URL | fcc-server | 可访问管理服务，用于在线身份核验；默认本机 8089 |
| FCC_SERVER_BASE_URL | fcc-admin | 可访问控制服务；默认本机 8085 |
| FCC_FLOW_RELOAD_TOKEN | 两个 Java 服务 | 同一个随机秘密；为空拒绝重载；反向代理和日志不得记录此头 |
| FCC_WS_ALLOWED_ORIGINS | fcc-server | 可选逗号分隔的明确页面 Origin，不允许 `*`；为空使用同源规则 |
| FCC_SIP_WS_URL / FCC_SIP_DOMAIN | fcc-admin | 浏览器可访问的 SIP WS/WSS 地址和注册域；HTTPS 页面需 WSS |
| FCC_SIP_ENCRYPTION_KEY | fcc-admin | 由秘密管理注入的 32 字节随机密钥，Base64 编码；缺失/错误则拒绝凭据操作 |
| FCC_DEFAULT_NODE_ID / Sidecar NODE_ID | 各自服务 | 与命令、事件实际所属节点一致 |

不得将上述秘密写入前端 VITE 环境变量。`VITE_FCC_SIP_PASSWORD` 已移除。反向代理必须透传 WebSocket 的 `Sec-WebSocket-Protocol`，服务端只回显 `fcc-agent`，不回显携带认证信息的 `auth.*`。认证头、子协议头、SIP 配置响应不得进入访问日志；生产使用 TLS/WSS。

## 发布顺序与既有开发数据

1. 备份 MySQL、分机配置与当前部署版本，配置并安全保管 SIP 加密密钥及服务间令牌。本轮未执行任何真实数据库更新。
2. 更新管理服务，使身份核验与本人 SIP 配置接口可用；更新控制服务，验证两者连通及重载鉴权。
3. 更新坐席端和管理端；同一发布窗口完成，旧坐席端未经认证的 WS 将被拒绝，不提供兼容通道。
4. 旧 credential_secret 若为明文，不能被新接口读取。需要在隔离环境重新开通/配置分机，再验证实际注册；不自动把历史字节当作密文或批量删除账户。密钥丢失不能恢复密文，密钥轮换必须另做受控重加密/重新开通流程。
5. 检查分机状态：开通失败记录 PROVISIONING_FAILED，不能提供注册凭据。当前重试补偿尚未实现，须先修复 Sidecar 开通问题再处理相关配置。
6. 核对保存/发布合法流程、真实来去电、保持/转接、双方挂机、DTMF、录音与话单。未知结果先查事实，不盲目重发控制命令。

本轮全新数据库基线删除了未使用的 `fcc_call_session.route_mode` 及其索引，流程身份使用 `flow_code`，业务类型使用 `model_type`，路由结果使用独立路由事实。项目不提供旧库兼容迁移；已有开发库先备份需要保留的配置，再按 `fcc-schema.sql` 重建。`credential_secret` 原字段继续承载字节；新格式为 FCC1 标识、12 字节随机 nonce、GCM 密文及认证标签。回退旧代码可能把密文误当密码，不能只回滚二进制而不核对配置和凭据格式。

## 验证结果

- Oracle JDK 21.0.12.1 下 `mvn -q -DskipTests compile` 已通过，未更改系统默认 Java。
- Flow Studio、动作执行器和事件协议的定向测试结果以本轮最终交付记录为准。
- 实际 MySQL、NATS/JetStream、FreeSWITCH、认证浏览器、跨网络媒体、Windows 交互和故障恢复仍需在部署环境验收。
- 本轮没有修改 Go 实现，也不据 Java 单元测试宣称 Sidecar/媒体联调通过。

定向命令（需 JDK 21）：

```text
mvn -q -Dtest=FlowDefinitionValidatorTest,SystemFlowModelsTest,FccEventMethodTest,FlowActionExecutorTest,FlowReloadBoundaryTest,FlowPublicationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

WebSocket 真实集成测试需另外注入 FCC_TEST_AGENT_WORK_NO 和该坐席的 FCC_TEST_AGENT_TOKEN；不在测试源码保存真实令牌。
