# FCC 话务全量接管与安全防盗打设计方案 (Call Takeover & Anti-Fraud Architecture)

> **文档版本**：v1.0.0  
> **创建日期**：2026-09-29  
> **状态**：已实施 (Implemented)  
> **适用模块**：FreeSWITCH 拨号计划、Go Sidecar、`fcc-server` 话务管控服务、`fcc-admin` 安全审计  

---

## 1. 背景与业务痛点

### 1.1 现状与安全隐患
在传统 IP-PBX 或未经严格改造的 FreeSWITCH 部署中，通常保留了本地拨号计划（如 `Local_Extension`、`default.xml` 中的默认 bridge 规则），允许分机之间直连或分机直接通过网关出局。这种架构在企业级联络中心存在严重的安全与合规漏洞：
1. **国际/高危盗打（Toll Fraud）风险**：
   - 外部 SIP 扫描探测（扫描 5060 端口）或弱口令分机被破解后，攻击者可直接向 FreeSWITCH 发送包含国际长途或高额声讯台前缀的 `INVITE` 请求。
   - 若拨号盘未做强业务鉴权，呼叫直接走中继网关接通，短时间内可能产生数万甚至数十万元的高额国际长途话费损失。
2. **坐席非在岗私自拨打外线**：
   - 话机置于工位，任何人员在非工作时间或坐席未登录工作台时拿起话机即可直拨外线，导致非业务通话不受控、甚至产生违规骚扰电话。
3. **通话脱管与审计缺失**：
   - 绕过业务核心（`fcc-server`）直接接通的通话，无法生成完整 CDR 话单、全程双轨录音丢失、满意度评价无法触发、无法纳入质检监控。
4. **主叫号码（CLI）伪造隐患**：
   - 终端若可在 SIP 报文的 `From` 头中自定义主叫号，可能因滥用主叫导致运营商中继线路被封停。

### 1.2 改造目标
- **100% 话务收口**：所有进出呼叫（分机互拨、外呼外线、进线客服、话机绑定）必须 100% 经由 `fcc-server` 状态机接管与调度，FreeSWITCH 本地严禁私自直通。
- **零信任（Zero Trust）准入鉴权**：未在 `fcc-server` 完成准入鉴权与坐席登录态校验的呼叫，FS 必须立即拒绝（`CALL_REJECTED`）。
- **防盗打与风控熔断**：建立高危号段黑名单、呼叫频率熔断、外显号码收口管控机制，彻底杜绝盗打风险。

---

## 2. 核心架构原则

```
+-----------------------------------------------------------------------------------+
|                            fcc-server 控制面 (大脑)                               |
|   [坐席在线态鉴权] -> [风控与防盗打规则] -> [外显DID/路由策略] -> [CDR/录音/评价调度] |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ JSON-RPC / NATS 指令与事件
                                         ▼
+-----------------------------------------------------------------------------------+
|                         Go Sidecar 代理层 (执行适配器)                            |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ ESL (Event Socket Library)
                                         ▼
+-----------------------------------------------------------------------------------+
|                     FreeSWITCH 媒体与信令交换层 (纯受控执行器)                    |
|   • 拨号盘封死本地 bridge 规则                                                    |
|   • 呼叫进入立即挂起 (park) 并上报，仅作为“哑执行节点” (Dumb Telephony Switch)     |
+-----------------------------------------------------------------------------------+
```

1. **FreeSWITCH 作为纯受控执行器（Dumb Telephony Switch）**：
   - FreeSWITCH 不做任何业务层路由和鉴权决策，不硬编码 `bridge user/xxx` 或 `bridge sofia/gateway/...`。
   - 所有进入 FreeSWITCH 的通话统一挂起（`park`），通道上下文通过 ESL 抛出给 Go Sidecar 并上报 `fcc-server`。
2. **唯一放行的本地白名单**：
   - 仅放行专用话机工号自助绑定通道（`0000`）。其余任何号码（内部短号、手机号、固话号等）必须全量走受控拦截流程。
3. **主叫身份与外显号码隔离**：
   - 坐席终端不知道真实 PSTN 中继网关凭证与配置。
   - 出局主叫外显号码（CLI）与线路运营商选择权 100% 收口在 `fcc-server` 的 `OutboundRoutePolicy` 中。

---

## 3. 详细设计方案（方案 A：拨号盘收口 + 坐席登录态强校验）

### 3.1 FreeSWITCH 拨号计划（Dialplan）收口改造

修改文件：`/opt/homebrew/etc/freeswitch/dialplan/default.xml`

#### 规则 1：话机工号自助绑定通道（唯一本地保留）
```xml
<!-- 话机拨打 0000 自助语音工号绑定 (放行进入绑定流程) -->
<extension name="fcc_phone_binding">
  <condition field="destination_number" expression="^0000$">
    <action application="set" data="fcc_flow_entry=PHONE_BINDING"/>
    <action application="set" data="hangup_after_bridge=false"/>
    <action application="set" data="park_after_bridge=true"/>
    <action application="set" data="absolute_codec_string=PCMU,PCMA"/>
    <action application="set" data="liberal_dtmf=true"/>
    <action application="park"/>
  </condition>
</extension>
```

#### 规则 2：全量外呼与分机拨号统一拦截接管（替代原 Local_Extension）
```xml
<!-- 封死本地 Local_Extension 的直接 bridge 行为，全量进入 fcc 集中接管池 -->
<extension name="fcc_controlled_intercept">
  <condition field="destination_number" expression="^(.*)$">
    <action application="set" data="hangup_after_bridge=false"/>
    <action application="set" data="park_after_bridge=true"/>
    <action application="set" data="absolute_codec_string=PCMU,PCMA"/>
    <action application="set" data="liberal_dtmf=true"/>
    <!-- 立即挂起通道并上报 CHANNEL_PARK 事件，等待 fcc-server 准入与调度 -->
    <action application="park"/>
  </condition>
</extension>
```
*注：彻底注销或下线原 `Local_Extension`、`group_dial_sales` 等包含 `<action application="bridge" .../>` 的默认本地直通规则。*

---

### 3.2 fcc-server 呼叫准入与坐席状态强校验（Admission Filter）

在 `fcc-server` 的话务进入入口（`OutboundCallService` / `TelephonyAdmissionService`）实现 **4 重准入过滤链（Admission Filter Chain）**：

```
SIP 话机拨号进入 (CHANNEL_PARK)
               │
               ▼
   [1. 设备绑定校验 (Device Check)] ───未绑定话机───► 挂机 (UNALLOCATED_NUMBER) / 播报未绑定
               │ 绑定正常
               ▼
   [2. 坐席在线状态校验 (Presence Check)] ───离线/未签到───► 挂机 (AGENT_OFFLINE) / 播报请先登录客户端
               │ 在线已签到
               ▼
   [3. 防盗打风控过滤 (Risk & Fraud Check)] ───高危号段/超频───► 拦截告警并挂机 (CALL_REJECTED)
               │ 风控通过
               ▼
   [4. 占用锁定与会话创建 (Reserve & Session)] ───成功锁定───► 调度客户侧呼叫与 Bridge 双方
```

#### 校验逻辑实现细节：
1. **分机归属与坐席绑定事实**：
   - 通过主叫分机号（如 `1007`）查询 `fcc_extension` / `fcc_agent`：
     - 若该分机未分配给任何坐席，直接拒绝呼叫并挂断。
2. **坐席实时登录态强校验（核心防盗打点）**：
   - 查询 `fcc_agent_presence` 中对应坐席的当前登录状态（`login_status`）：
     - 若 `login_status != 'ONLINE'`（坐席处于 `OFFLINE` 离线状态）：
       - **阻断行为**：立即调用下发挂机指令，原因为 `CALL_REJECTED` 或 `AGENT_OFFLINE`。
       - **友好交互**（可选）：在挂断前向通道播报 3 秒语音提示：“当前话机坐席未在客户端登录，无法发起呼叫”。
3. **坐席忙闲状态互斥与锁定**：
   - 检查 `fcc_agent_presence.work_status` 与 `active_call_id`：
     - 执行原子排他更新 `agents.reserveOriginated(workNo, callId)`；若已被占用，抛出并发冲突并挂断。

---

### 3.3 防盗打风控拦截器（Anti-Fraud Risk Filter）

防范外部黑产扫描或被盗账号产生巨额账单：
1. **高危号段黑名单过滤**：
   - **国际长途禁用**：默认拦截 `00`、`+`、`011` 开头的所有被叫号码（联络中心默认不开通国际出局，需特权配置）。
   - **敏感声讯与特服号拦截**：拦截 `168`、`950`、`96`、`400`（400只作呼入，禁止外呼反拨扣费）。
2. **频次与异常熔断（Anti-Brute Force）**：
   - 单分机外呼频次上限：1 分钟内连续拨号超过 5 次，自动触发 10 分钟临时熔断风控，并向系统管理员产生高危告警日志。
   - 批量外呼防盗打：必须携带已备案的 `dialJobId`，非任务名单外呼严禁批量发起。

---

## 4. 涉及模块改造清单

| 模块 / 仓库 | 涉及文件 | 改造内容描述 |
| :--- | :--- | :--- |
| **FreeSWITCH** | `/opt/homebrew/etc/freeswitch/dialplan/default.xml` | 注销本地 `bridge` 规则，收口 `Local_Extension`，所有呼叫统一 `park` 上报。 |
| **fcc-server** | `com.chandler.fcc.server.telephony.application.OutboundCallService` | 在 `handleAgentOriginatedCall` 增加坐席 `ONLINE` 登录态强校验与防盗打号段拦截。 |
| **fcc-server** | `com.chandler.fcc.server.security.CallAdmissionService` (新增) | 抽离呼叫准入控制（CAC），统一负责号段风控、频次限流与黑白名单检查。 |
| **fcc-server** | `com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService` | 记录因鉴权拒绝产生的拦截事件日志与拦截话单（状态标为 `REJECTED`）。 |
| **fcc-admin-web** | `src/views/CdrReportView.vue` / 告警监控 | 增加风控拦截状态标识（显示“被风控拦截 / 离线拒拨”原因码）。 |

---

## 5. 验收测试用例 (Acceptance Criteria)

- [x] **用例 1（离线分机盗打拦截）**：
  - 坐席客户端未登录（1007 处于离线状态），在 SIP 话机（Zoiper / 硬件话机）上拨打 1008 或外线手机号。
  - **预期结果**：FreeSWITCH 立即挂断，SIP 终端收到 `403 Forbidden` 或 `CALL_REJECTED`，控制台记录“分机未登录，禁止外呼”审计日志。
- [x] **用例 2（在线坐席合法外呼）**：
  - 坐席在客户端成功登录并置闲，话机拨打 1008。
  - **预期结果**：`fcc-server` 校验通过，先接管 1007，随后发起呼叫 1008 并成功 bridge，产生包含完整 `flowCode` 的正规话单。
- [x] **用例 3（高危国际号/黑名单拦截）**：
  - 无论是否登录，拨打 `001-800-xxxx` 或高危国际号码。
  - **预期结果**：准入过滤器直接识别出高危前缀，就地拒绝挂机，不发起任何中继外呼。
- [x] **用例 4（自助工号绑定放行）**：
  - 离线话机拨打 `0000`。
  - **预期结果**：正常放行进入 `fcc_phone_binding` 流程，成功收取按键并换绑。
