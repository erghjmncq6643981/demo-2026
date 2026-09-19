# 箱箱呼叫中心 - 通话流程模型与过程详情设计规范

## 1. 架构总览：统一的「Stage + Action」两级模型

在箱箱呼叫中心体系中，**每一次通话都是特定业务流程（Flow）在电信网络中的一次实例运行**。
为了打通**设计态（IVR 流程编排）**与**运行态（CDR 过程详情追踪）**，前端与后端统一遵循**“阶段（Stage）+ 动作（Action）”**的两级分层流水线模型：

```
+-----------------------------------------------------------------------------------+
|                           电信级通话生命周期 (Flow Lifecycle)                       |
+-----------------------------------------------------------------------------------+
|  1. TRIGGER (触发阶段)  ──>  2. ROUTE (路由排队阶段)  ──>  3. CONNECTED (通话阶段)  ──> 4. END (结束阶段)
|  - CHANNEL_CREATE          - READ_DTMF (按键采集)      - BRIDGE (通道双通桥接)       - RECORD_STOP
|  - ANSWER (摘机应答)        - 3 种路由策略判定          - RECORD_START (录音)        - POST_SURVEY (满意度)
|  - PLAY_WELCOME (迎宾语)   - QUEUE_ACD (队列排队)      - BUSINESS_EVENT (转接/三方)  - HANGUP (挂机释放)
|                            - CALL_AGENT (坐席振铃)
+-----------------------------------------------------------------------------------+
```

---

## 2. IVR 呼入流程三大路由模式设计

呼入流程（Inbound Flow）是呼叫中心中最为复杂的链路。前端在 `IvrFlowView.vue` 中提供了 3 种可无缝切换的路由模式，并且与右侧时序泳道图、仿真测试日志强联动：

### 2.1 路由模式对比与规格

| 路由模式 | 标识符 (`routeMode`) | 核心原理 | 适用场景 | 决策延时 | 异常容灾兜底策略 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DID 直达** | `DID_DIRECT` | 根据主叫拨打的 DID 接入码，直接映射到特定工号或专属坐席组，跳过 IVR 语音按键 | 大客户 1V1 专属管家、员工分机直拨、VIP 直线 | < 50ms | 目标坐席忙/离线时转溢出客服组 |
| **多维规则引擎** | `RULE_ENGINE` | 依据时间段（工作日/夜间/节假日）、客户画像标签（VIP/新客/黑名单）、地域归属阶梯式判定 | 综合政企服务热线、时段化客服分流、高价值客户优先服务 | < 100ms | 规则不命中时自动进入默认「通用客服组」 |
| **业务接口回调** | `HTTP_CALLBACK` | 应答后向外部业务中台发送 HTTP POST，由业务系统计算返回目标坐席/组 | 订单查询（顺丰查快递小哥）、网约车行程转接、CRM 动态指派 | 100~500ms | **超时（默认800ms）或 5xx 异常时，毫秒级无缝降级至兜底客服组** |

### 2.2 路由参数配置抽屉与数据契约

在 `IvrFlowView.vue` 中，点击「路由参数配置」可唤起配置弹窗，数据结构直接对应后端 `ivr_flow_config.route_config_json`：

```typescript
export interface InboundRouteConfig {
  mode: 'DID_DIRECT' | 'RULE_ENGINE' | 'HTTP_CALLBACK';
  
  // 1. DID 直达配置
  directTargetType: 'AGENT' | 'GROUP';
  directAgentWorkNo?: string;     // 如 '8001' (钱丁君)
  directGroupId?: string;         // 如 'VIP-SUPPORT-01'
  
  // 2. 规则引擎配置
  rules: Array<{
    ruleId: string;
    priority: number;
    conditionType: 'TAG_VIP' | 'TIME_WORKDAY' | 'TIME_NIGHT' | 'CALLER_REGION';
    conditionValue: string;
    targetGroupId: string;
  }>;
  defaultGroupId: string;         // 默认通用客服组
  
  // 3. 业务接口回调配置
  callbackUrl: string;            // 如 'https://api.business.local/ivr/v1/dispatch'
  timeoutMs: number;              // 超时阈值，默认 800ms
  fallbackGroupId: string;        // 超时/失败降级兜底组，如 'DEFAULT-FALLBACK-GROUP'
}
```

---

## 3. CDR 通话过程详情（Call Journey Trace）设计

在 `CdrReportView.vue` 中，点击任一话单的「过程详情」按钮，将弹出高颜值的 **全生命周期时序流水线卡片（Call Journey Trace）**。

### 3.1 界面布局与核心要素

1. **顶部 Header 概览栏**：
   - 通话唯一 ID（Call ID）、主叫号码与被叫号码；
   - 路由模式标签（DID直达 / 规则引擎 / 业务接口回调）；
   - 挂机满意度评分徽章（1-5 星）。
2. **全局录音播放条**：
   - 针对有录音文件的正常通话，顶部集成 HTML5 Audio 音频播放器，支持播放、暂停、进度条拖动与下载。
3. **4 大阶段流水线卡片（Stage Pipeline Cards）**：
   - **Stage 1: 触发应答 (TRIGGER)**：包含通道建立时间、系统应答时间、迎宾欢迎语播报。
   - **Stage 2: 路由决策与排队 (ROUTE)**：
     - 若为 `DID_DIRECT`：展示 DID 匹配过程及直达工号；
     - 若为 `RULE_ENGINE`：展示规则引擎仲裁耗时及命中规则标签；
     - 若为 `HTTP_CALLBACK`：展示调用业务接口耗时、接口响应参数；若发生降级，以警告橙色徽章标注「触发超时降级 ➔ 转入兜底组」。
     - 展示 ACD 队列排队时长、坐席振铃时长。
   - **Stage 3: 通话服务 (CONNECTED)**：
     - 展示通道双向桥接（Bridge）成功时刻、双轨录音启动打点、通话中交互事件。
   - **Stage 4: 结束收尾 (END)**：
     - 展示录音结束与 OSS 归档耗时、客户满意度评价结果、挂机释放原因。
4. **动作卡片参数下钻 (Payload Details)**：
   - 针对重要节点（如 HTTP 回调、规则决策），卡片内以代码块格式展示入参和出参明细，方便运维排障与质检回溯。

---

## 4. 前端组件代码清单

| 页面文件 | 修改范围与新增特性 |
| :--- | :--- |
| `src/views/IvrFlowView.vue` | 1. 增加呼入 3 种路由策略切换单选组。<br>2. 架构时序泳道图动态联动 3 种模式节点。<br>3. 增加路由参数配置模态框。<br>4. 仿真测试控制台日志支持 3 种模式真实时序推演。 |
| `src/views/CdrReportView.vue` | 1. 定义 `TraceStep` 轨迹实体与 `routeMode` 等扩展字段。<br>2. 丰富真实 Mock 数据，覆盖 3 种路由模式、正常通话与异常降级通话。<br>3. 全面重构过程详情弹窗，落地 4 阶段流水线卡片 + 录音播放器 + Payload 详情展开。 |

---

## 5. 生产联调与实施建议

1. **接口对齐**：
   - IVR 流程保存接口：`POST /api/ivr/flow/save`，传参包含 `routeMode` 与 `routeConfigJson`。
   - 通话时序详情接口：`GET /api/cdr/timeline?callId={callId}`，直接返回 `call_timeline` 列表。
2. **高可用保障**：
   - 业务接口回调模式下，后端必须实施 `800ms` 熔断超时器，前端在仿真器中提供“模拟接口超时”测试选项。
