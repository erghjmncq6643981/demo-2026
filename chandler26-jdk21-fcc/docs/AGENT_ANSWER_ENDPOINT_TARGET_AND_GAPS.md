# 坐席三种接听方式：目标模型、当前偏差与实施验收

更新日期：2026-09-22。

## 1. 文档目的与状态

本文是供开发人员和实现型 AI 使用的跨工程实施规格，描述坐席 `WEBRTC` 软电话、`SIP` 物理话机和 `MOBILE` 手机三种接听方式的目标行为、当前代码偏差、改造顺序与验收标准。

本文中的“目标”不代表已经实现；“当前实现”只表示源码中已有对应代码，不能替代真实 FreeSWITCH、SIP、WebRTC、浏览器或 Windows 验证。当前阶段只要求交付 `WEBRTC` 与 `SIP`，`MOBILE` 只保留稳定领域模型和扩展边界，不接入真实运营商呼叫。

涉及工程：

- `chandler26-jdk21-fcc`：管理面、运行面、Call/Leg/Bridge 事实和话务编排；
- `chandler26-fcc-client-web`：坐席客户端、JsSIP/WebRTC 媒体、物理话机模式界面；
- `chandler25-fs-sidecar-agent`：NATS/FNode 到 FreeSWITCH 的控制与规范事件；
- FreeSWITCH：SIP/WebSocket 注册、拨号计划、媒体与话道事实来源。

## 2. 产品目标

一个坐席可以拥有多种终端绑定，但任意时刻只能有一种“当前接听方式”。接听方式决定后续新呼叫路由到哪个终端，不删除其他历史绑定，也不改变正在进行的通话。

| 接听方式 | 规范值 | 终端身份 | 当前范围 | 目标行为 |
| --- | --- | --- | --- | --- |
| 客户端软电话 | `WEBRTC` | 坐席工号 | 本期实现 | 客户端登录后取得本人 SIP 配置，以工号作为 SIP 用户名自动注册；在客户端中接听和承载媒体 |
| 物理 SIP 话机 | `SIP` | 已绑定 SIP 分机 | 本期实现 | Linphone、Zoiper 或真实话机先注册 FreeSWITCH，再拨 `0000` 输入工号完成绑定；呼入、外呼均振铃该话机 |
| 个人手机 | `MOBILE` | 坐席手机号 | 本期不实现 | 未来通过运营商中继先呼叫坐席手机，再呼叫客户并桥接 |

“使用工号登录软电话”只表示 SIP 用户名/分机标识为坐席工号，不表示 SIP 密码等于工号或坐席登录密码。WebRTC SIP 注册口令必须独立随机生成、加密保存，仅通过已认证的本人接口按需返回，并且只保存在客户端内存中。

## 3. 不可破坏的领域与协议约束

1. `call_id` 是 FCC 业务通话聚合标识；`channel_uuid` 是单个 FreeSWITCH Leg；两者不得互换。
2. 客户端不能直接连接 NATS 或 ESL，也不能构造 FNode/ESL 命令。
3. HTTP 命令受理不是最终话务结果。最终振铃、接听、桥接和挂机状态必须由规范 FreeSWITCH 事件、业务 WebSocket 与 SIP Session 对账。
4. `WEBRTC`、`SIP`、`MOBILE` 是三种业务接听方式；不要用 `PHONE`、`PSTN`、`SOFTPHONE` 等别名制造兼容分支。
5. 终端绑定清单、当前接听方式、终端注册状态、坐席工作状态是不同概念：
   - 绑定清单回答“坐席拥有哪些终端”；
   - 当前接听方式回答“新呼叫应路由到哪里”；
   - 注册状态回答“该 SIP/WebRTC 终端是否实际在线”；
   - 工作状态回答“坐席是否 READY/BUSY/REST/ACW”。
6. 接听方式切换不能修改进行中 Call/Leg 的终端快照。处于 `RINGING`、`CALLING`、`CONNECTED` 或 `ENDING` 时必须拒绝切换；`ACW` 是否允许切换必须由一个明确策略统一决定，前后端不得各自猜测。
7. 呼入和外呼都由 `fcc-server` 创建并持有业务 Call。软电话只承担 SIP 信令与 WebRTC 媒体，不承担业务外呼编排。
8. 新外呼统一采用“坐席先接听”：先呼叫当前接听终端，坐席接听后再呼叫客户并桥接。
9. 所有 Java `Long` ID 在前端按不透明字符串处理。
10. 手机接听本期不得以假成功、模拟振铃或硬编码号码冒充实现。

## 4. 目标数据语义

### 4.1 终端绑定

`fcc_agent_endpoint_binding` 保存坐席拥有的终端绑定历史：

| `endpoint_type` | `endpoint_value` | 说明 |
| --- | --- | --- |
| `WEBRTC` | 坐席工号 | 与坐席一一对应的 WebRTC SIP 分机 |
| `SIP` | SIP 分机号 | 由物理话机拨 `0000` 后绑定，或由授权管理操作维护 |
| `MOBILE` | 规范化手机号 | 未来运营商线路使用；本期只允许维护，不允许发起真实呼叫 |

目标实现必须保证：

- 同一坐席最多有一个有效 `WEBRTC` 绑定；
- 同一物理 SIP 分机同时最多绑定一个有效坐席；
- 切换当前接听方式只调整活跃选择，不删除其他绑定历史；
- 当前活跃终端不能依赖“查询结果第一行”这一隐式约定，应具有数据库可约束、可审计的单一事实；
- 切换操作使用事务和并发保护，并记录操作者、旧终端、新终端、结果和时间；
- 对 `WEBRTC` 与 `SIP` 路由前同时验证资源启用状态和最新注册状态。

当前表使用 `priority=0` 隐式表达活跃终端。实现前必须决定并固定一种方案：继续使用 `priority=0` 并增加唯一性/并发约束，或增加显式活跃字段/独立活跃终端事实。若变更表结构，必须同步更新 `docs/fcc-schema.sql`，并提供前向 DDL、数据回填、兼容窗口和回滚/修复方案。

### 4.2 通话事实

每个坐席 Leg 必须持久化当次呼叫使用的：

- `endpoint_type`；
- `endpoint_id` 或规范 `endpoint_value` 快照；
- `node_id`；
- `channel_uuid`；
- 源事件时间和接收时间。

切换接听方式只影响之后创建的 Leg，不能覆盖历史 CDR 或活动 Leg。

## 5. 目标运行链路

### 5.1 WebRTC 软电话登录与注册

```text
坐席登录 fcc-client
  -> fcc-admin 返回当前接听方式 WEBRTC
  -> fcc-client 调用本人 /api/admin/auth/sip-config
  -> fcc-admin 只返回本人 ENABLED WebRTC 分机、WSS 地址、域和临时可用注册凭据
  -> fcc-client 以 sip:{workNo}@{domain} 初始化 JsSIP UA
  -> FreeSWITCH 返回 REGISTER 结果
  -> 注册事实通过 FreeSWITCH/Sidecar 事件进入后端
  -> 客户端分别展示业务 WebSocket、SIP 注册和媒体状态
```

要求：

- `sip-config` 响应使用 `Cache-Control: no-store`；不得记录或持久化明文 SIP 密码；
- HTTPS 页面只能连接可信 `wss://` SIP WebSocket；本机开发例外需显式限定；
- Electron 只允许当前可信工作台来源申请音频权限，不得继续拒绝所有权限，也不得放开摄像头、屏幕等无关权限；
- 注册断开、认证失败、配置缺失和网络不可达必须是不同可见状态；
- 注销、退出登录、切换为非 WebRTC 方式时，幂等终止 Session、停止 UA、释放媒体 Track 和清理监听器。

### 5.2 呼入到 WebRTC

```text
客户呼入
  -> FreeSWITCH/Sidecar 上报客户 Leg
  -> fcc-server 创建/恢复业务 Call 并选择 READY 坐席
  -> 解析坐席当前活跃终端为 WEBRTC(workNo)
  -> FNode.Dial 呼叫 user/{workNo}
  -> 客户端收到 SIP INVITE
  -> 业务 WebSocket 发送同一 call_id 的 SCREEN_POP
  -> 客户端按 SIP Session 与 call_id 对账后显示振铃
  -> 坐席点击接听，JsSIP Answer
  -> FreeSWITCH 事件确认坐席 Leg 接听和 Bridge
  -> 客户端进入 CONNECTED
```

SIP INVITE 与 `SCREEN_POP` 可能先后到达、重复或缺失。客户端必须以稳定 Session/Leg 关联表对账，不得用无身份的回调结束“当前任意通话”。临时 `sip-{sessionId}` 只可用于短暂关联，拿到真实 `call_id` 后必须原子替换并保留 Session 映射。

### 5.3 呼出到 WebRTC

```text
坐席在客户端输入客户号码
  -> POST /api/telephony/call/outbound
  -> fcc-server 校验本人身份、READY 状态、号码路由和当前 WEBRTC 终端
  -> 先持久化 Call、坐席 Leg 意图和命令事实
  -> FNode.Dial 呼叫 user/{workNo}
  -> 客户端收到 SIP INVITE 并接听
  -> fcc-server 再呼叫客户
  -> FreeSWITCH Bridge 两个 Leg
  -> 事件驱动最终 CONNECTED/FAILED 状态
```

禁止客户端直接使用 `JsSIP.call(客户号码)` 绕过 `fcc-server`。客户端的 JsSIP UA 是媒体终端，不是业务呼叫编排器。

### 5.4 物理 SIP 话机绑定

```text
Linphone/Zoiper/真实 SIP 话机注册 FreeSWITCH
  -> 从已认证分机拨打 0000
  -> Sidecar 上报真实 node_id、channel_uuid 和认证分机
  -> fcc-server 校验该分机为 ENABLED SIP 资源
  -> 播放提示并收取坐席工号 DTMF
  -> 事务锁定坐席与分机
  -> 失效冲突的旧 SIP 绑定并追加新绑定事实
  -> 播放成功/失败提示并挂机
```

工号只是绑定目标，不能代替发起话机的 SIP 身份。未知分机、未注册分机、停用坐席、冲突绑定和活动通话占用必须明确拒绝并审计。

### 5.5 呼入与呼出到物理 SIP 话机

当活跃终端为 `SIP(extension)` 时，呼入与外呼均由 `fcc-server` 通过逻辑 FNode 命令呼叫绑定分机。坐席在物理话机上接听；`fcc-client` 只显示业务状态、客户资料和通话控制，不承载 WebRTC 媒体，也不能因业务 WebSocket显示“已接听”而伪造 SIP 媒体状态。

### 5.6 手机接听边界

本期只保留 `MOBILE(phone)` 绑定和选择模型。选择 `MOBILE` 时，若运行端尚未启用对应能力，后端必须明确拒绝新呼叫或禁止选择，不能回落到 WebRTC/SIP，也不能返回成功。

未来目标是通过受配置控制的运营商中继先呼叫坐席手机，坐席接听后再呼叫客户。借鉴旧 `call-center-backend` 时只参考业务闭环，不复制旧协议、身份字段或直接 ESL 调用。

## 6. 当前已有实现

以下能力在当前源码中存在，但仍需结合偏差和验收章节判断是否可用：

### 6.1 `fcc-admin`

- 创建坐席时尝试按工号创建独立 `WEBRTC` 分机，并生成随机 SIP 注册口令；
- SIP 口令使用配置密钥加密保存；
- `/api/admin/auth/sip-config` 读取本人启用的 WebRTC 分机配置；
- Endpoint DTO、绑定表和服务代码已出现 `WEBRTC`、`SIP`、`MOBILE` 三类；
- 管理服务已有查询终端清单和切换接听方式的接口。

### 6.2 `fcc-server`

- `PhoneBindingService` 已识别 `0000`、校验认证分机、收取工号并更新绑定；
- 呼入服务已有选择坐席并拨打坐席分机的路径；
- 外呼服务已有“坐席先接听”模板；
- Call/Leg/Bridge/Command/Event 和流程执行事实已有持久化基础；
- 注册事件进入 Redis/事实处理的基础代码已经存在。

### 6.3 `fcc-client`

- 已引入 JsSIP；
- 已实现 UA 注册、INVITE、Answer、Hangup、Mute、DTMF 和远端音频 Track 播放的基础代码；
- 已有业务 WebSocket 重连、心跳以及基础 Call 状态机；
- 已有软电话拨号盘和接听界面代码。

上述“已有”不表示三种接听方式已经形成可用闭环。

## 7. 当前偏差

### 7.1 P0：阻断 WebRTC 使用

| 偏差 | 当前证据 | 目标修复 |
| --- | --- | --- |
| 客户端强制所有接听方式为 `SIP` | `agentStore` 登录、加载 Endpoint 时写死 `SIP`，并拒绝切换到非 `SIP` | 严格映射后端 `WEBRTC/SIP/MOBILE`；只允许选择真实存在且可用的绑定 |
| 软电话组件正常流程不可达 | `SoftphoneDialer` 仅在 `endpoint === WEBRTC` 时加载 | 恢复 WebRTC 选择和登录后的自动注册 |
| Electron 拒绝麦克风 | 桌面主进程对所有权限请求返回 false | 仅对白名单来源和 `media` 音频授权，显式拒绝其他权限，并增加测试 |
| WebRTC 外呼绕过 FCC | SIP 注册成功时客户端直接 `JsSIP.call(客户号码)` | 所有业务外呼只调用 `fcc-server`；JsSIP 只应答坐席侧 INVITE |

### 7.2 P0：当前活跃终端事实不够可靠

| 偏差 | 风险 | 目标修复 |
| --- | --- | --- |
| `fcc-admin` 以 `priority` 排序后的第一条绑定推断当前终端 | 并发切换或重复零优先级时结果不确定 | 增加数据库约束与原子切换；查询必须得到唯一活跃终端 |
| 运行端主要读取“extension”而非显式 endpoint 类型和值 | `MOBILE`、`WEBRTC` 与物理 `SIP` 语义容易混合 | 引入窄的 EndpointSelection/EndpointRoute 结果，包含类型、值、资源和注册状态 |
| 切换接口没有以活动 Call 事实作为统一前置条件 | 通话中切换可能使新旧状态分裂 | 后端原子拒绝活动阶段切换；前端同时禁用并显示原因 |
| 绑定切换和话机 `0000` 绑定的活跃语义可能互相覆盖 | 管理 API 与运行绑定产生竞争 | 统一一套活跃选择规则和审计事实，增加并发测试 |

### 7.3 P1：SIP Session 与业务 Call 对账不足

- WebRTC 服务只保存一个 `currentSession`，新 Session 会覆盖旧 Session；
- Session 结束回调没有稳定 `call_id`，延迟的旧 Session 可能结束新通话；
- `answer()` 失败返回值未被 Store/UI处理；
- SIP `failed`、正常 `ended`、本地拒接、远端挂机、权限拒绝和 ICE 失败被过度合并；
- 未接通失败也可能进入 ACW；
- 没有忙线/第二来电的明确拒绝策略；
- SIP 与业务 WebSocket 的重复、乱序、晚到事件只有部分状态机测试。

目标是以 Session ID、Call ID 和坐席 Leg Channel UUID 建立显式关联；任何结束、接听或失败事件必须携带关联身份，终态副作用保持幂等。

### 7.4 P1：WebRTC 媒体能力不足

- 没有麦克风权限预检和可恢复交互；
- 没有输入/输出设备枚举、选择、设备拔插处理和扬声器切换；
- 没有监听 ICE/PeerConnection `disconnected`、`failed`、`closed`；
- 没有 TURN 可达性、单向无声和媒体超时判断；
- 远端音频自动播放被拦截后只有日志，没有用户恢复入口；
- 静音状态先修改 UI，再调用媒体 API，失败时不会回滚；
- 没有真正的 `getStats()` 质量采集。

当前界面中的固定 MOS、RTT、丢包和编解码展示不是实时测量，必须删除、标记不可用或替换为真实 WebRTC Stats。

### 7.5 P1：注册、可用性与恢复

- SIP 注册状态目前主要存在于客户端；运行路由还需确认使用后端最新注册事实；
- 需要区分配置缺失、密码错误、WSS 失败、注册超时、注册被拒绝和网络断开；
- 需要验证应用隐藏、Windows 锁屏、休眠唤醒、网络切换和重新登录后的 UA/Session 清理；
- 缺少真实 FreeSWITCH SIP-WSS、DTLS-SRTP、NAT/TURN 和双向媒体证据；
- `MOBILE` 路由尚未实现，本期不得进入可用状态。

### 7.6 P2：结构与文档一致性

- `README` 同时描述 WebRTC 软电话和“桌面交付目标为物理 SIP 话机”，需要在实现完成后统一为真实现状；
- 客户端部分组件直接调用 API，软电话编排应下沉到明确的 call/media coordinator；
- SIP/WebRTC 服务缺少聚焦单元测试；
- 前端已有业务状态机测试，但未覆盖媒体权限、ICE、第二 Session、延迟旧 Session、注册重连和终端切换。

## 8. 建议实施顺序

### 阶段 A：固定领域模型和活跃终端事实

- [ ] 在 `fcc-common` 增加或收敛接听方式枚举，只有 `WEBRTC/SIP/MOBILE`，每个值带中文描述。
- [ ] 明确 `fcc_agent_endpoint_binding` 的绑定历史与活跃终端表达方式。
- [ ] 为“一个坐席一个活跃终端”“一个物理分机一个有效坐席”提供数据库和应用层并发保护。
- [ ] 新增 Endpoint 查询端口，返回类型、值、资源状态、注册状态和可用性原因。
- [ ] 统一管理切换与 `0000` 绑定对活跃终端的处理和审计。
- [ ] 暂时禁止选择 `MOBILE`，或明确返回 `NOT_IMPLEMENTED`。

### 阶段 B：修复客户端接听方式

- [ ] 移除客户端强制 `SIP` 的逻辑，严格消费后端规范值。
- [ ] 展示三种方式；未实现的 `MOBILE` 明确禁用并标注原因。
- [ ] 登录或切换为 `WEBRTC` 后自动获取本人 SIP 配置并注册。
- [ ] 切换为 `SIP` 后销毁 WebRTC UA/Session，只显示物理话机状态。
- [ ] 活动通话期间禁止切换。
- [ ] 不把 SIP 凭据写入 localStorage、日志、URL、错误上报或桌面配置文件。

### 阶段 C：统一呼入/外呼编排

- [ ] 删除软电话直接呼叫客户号码的路径。
- [ ] WebRTC 与 SIP 外呼都走 `fcc-server` 坐席先接听流程。
- [ ] 运行端按活跃 EndpointRoute 生成坐席侧拨号目标。
- [ ] 呼入路由按 EndpointRoute 呼叫工号或绑定分机。
- [ ] Call/Leg 持久化终端类型和值快照。
- [ ] 补齐失败、超时、无应答、注册离线和晚到事件处理。

### 阶段 D：媒体可靠性和桌面权限

- [ ] Electron 仅允许可信源的音频媒体权限，并覆盖允许/拒绝测试。
- [ ] 增加麦克风预检、设备选择、设备变化和自动播放恢复交互。
- [ ] 监听 PeerConnection/ICE 状态并形成独立可见失败状态。
- [ ] Session 与 Call/Leg 显式关联，防止旧 Session 结束新通话。
- [ ] 增加真实 `getStats()` 或移除虚假质量指标。
- [ ] 完成资源清理和休眠/网络切换后的注册恢复。

### 阶段 E：跨工程验收

- [ ] 同步 Java、客户端、Sidecar、FreeSWITCH 配置和文档。
- [ ] 先完成单节点真实通话，再做故障、重启和重复事件测试。
- [ ] 记录外部依赖版本、部署地址类别和测试证据，不记录凭据或真实个人号码。
- [ ] 未具备真实环境时明确标注为未验证，不把单元测试描述为端到端通过。

## 9. 必须具备的测试

### 9.1 后端单元与集成测试

- 创建坐席生成唯一 WebRTC 工号分机和加密注册口令；
- 只有本人可读取本人 SIP 配置，响应不可缓存；
- `WEBRTC -> SIP -> WEBRTC` 切换保留两种绑定，且始终只有一个活跃终端；
- 两个并发切换请求不能产生两个活跃终端；
- 活动通话期间切换被拒绝；
- 未注册/停用 WebRTC 或 SIP 终端不进入可呼叫路由；
- `0000` 绑定成功、未知分机、错误工号、冲突分机、重复 DTMF 和重复事件；
- WebRTC 和 SIP 呼入都使用正确 EndpointRoute；
- WebRTC 和 SIP 人工外呼都先呼叫坐席，再呼叫客户；
- 任何失败都不会伪造 Answered/Connected；
- 重复、乱序、晚到挂机事件不重复释放坐席或结束新通话。

### 9.2 客户端测试

- 后端返回三种接听方式时不被强制改写；
- `WEBRTC` 登录自动注册，`SIP` 登录不创建 JsSIP UA；
- 切换和注销幂等清理 UA、Session、Track 和监听器；
- 麦克风拒绝、无设备、WSS 失败、认证失败、ICE 失败分别展示正确状态；
- SIP INVITE 与 SCREEN_POP 任意先后顺序均关联到同一 Call；
- 重复和延迟旧 Session 事件不能影响新 Call；
- 第二来电按既定忙线策略处理；
- 接听失败不停止必要提示且不进入 Connected；
- 本地/远端挂机只产生一次终态和一次 ACW；
- 软电话外呼只调用 FCC REST，不直接 `JsSIP.call(客户号码)`；
- 音频设备切换、静音失败回滚和自动播放恢复有聚焦测试。

### 9.3 真实环境验收

至少使用一个浏览器/Electron 客户端、一个 Linphone 或 Zoiper、一个 FreeSWITCH 节点完成：

1. WebRTC 工号注册成功，呼入可振铃、接听并双向通话；
2. WebRTC 人工外呼先振铃客户端，接听后呼叫客户并桥接；
3. Linphone/Zoiper 拨 `0000` 完成工号绑定；
4. 切换到 SIP 后，呼入振铃物理话机，客户端不请求麦克风；
5. SIP 人工外呼先振铃物理话机，拿起后呼叫客户；
6. 从 SIP 切回 WebRTC 后只振铃软电话；
7. 通话中尝试切换被前后端共同拒绝；
8. 断网、WSS 断开、FreeSWITCH 重启、客户端重启和远端挂机后状态可恢复或明确进入未知/失败；
9. CDR 中 Call、双方 Leg、终端类型、Channel UUID、Bridge、开始/接听/结束时间和挂机原因一致；
10. 日志、接口、浏览器存储和安装目录不存在 SIP 明文口令或真实测试号码泄漏。

`MOBILE` 不在本期真实验收范围内。

## 10. 完成定义

只有同时满足以下条件，才能将 WebRTC 软电话描述为“已实现”：

- 正常登录/切换流程能够选择 `WEBRTC`，无需修改 localStorage 或手工调用内部代码；
- Electron 或受支持浏览器可以取得音频权限并完成真实双向媒体；
- 呼入、外呼均由 `fcc-server` 持有业务 Call，客户端不绕过控制面；
- SIP Session、业务 Call 和坐席 Leg 可以稳定关联；
- 权限拒绝、注册失败、ICE 失败、远端/本地挂机、重复和乱序事件均有明确状态与测试；
- 真实 FreeSWITCH 环境完成本节验收并保存不含敏感信息的证据；
- Java 编译与测试、前端构建与聚焦测试、Sidecar 测试、Mapper XML 解析和 `git diff --check` 均通过；外部依赖不可用项如实记录。

只有完成 `0000` 真实绑定以及 SIP 呼入、坐席先接听外呼和切换回 WebRTC 的闭环，才能将物理话机接听描述为“已实现”。

手机接听在本期只能描述为“领域模型预留，运行能力未实现”。

## 11. 实现入口

- 后端当前架构：[DESIGN.md](./DESIGN.md)
- 基线数据模型：[fcc-schema.sql](./fcc-schema.sql)
- WebRTC 凭据边界：`fcc-admin/.../AgentSipConfigService.java`
- 三端查询与切换：`fcc-admin/.../AgentService.java`
- 物理话机绑定：`fcc-server/.../PhoneBindingService.java`
- 呼入编排：`fcc-server/.../InboundCallService.java`
- 坐席先接听外呼：`fcc-server/.../OutboundCallService.java`
- 客户端 Endpoint 状态：`chandler26-fcc-client-web/src/stores/agentStore.ts`
- 客户端 SIP/WebRTC：`chandler26-fcc-client-web/src/services/sipWebRtcService.ts`
- 客户端软电话界面：`chandler26-fcc-client-web/src/components/telephony/SoftphoneDialer.vue`
- Electron 权限入口：`chandler26-fcc-client-web/desktop/main.cjs`
- 跨工程验收背景：[fcc-cross-machine-acceptance.md](../../docs/fcc-cross-machine-acceptance.md)

实现型 AI 必须先检查上述真实代码和各工程 `AGENTS.md`，再按阶段提交改动。不得仅修改界面使软电话“看起来可选”，也不得以单元测试、模拟 Session 或固定指标替代真实 SIP/WebRTC 媒体验收。
