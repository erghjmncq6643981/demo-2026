# 箱箱呼叫中心 - IVR 流程纵向流转模型与二期动态编排/版本发布架构规范

## 1. 架构演进背景与双期规划

在传统呼叫中心界面中，横向（从左到右）泳道图在宽屏或多节点下极易出现横向拥挤、纵向留白严重、流程层次不直观的问题。
按照业务实际流转规律，**电话进线或外呼发起，在电信网络中必然遵循严格的时序先后顺序**。因此，系统进行了针对性的双期演进规划：

- **第一期（已落地）**：真实系统的自上而下纵向流转模型。
  - 将横向 6 列泳道图彻底重构为**「从上 -> 下」纵向流水线（Vertical Pipeline）**；
  - 划分为 5 个清晰阶段（触发应答 ➔ 路由分流 ➔ 通话中 ➔ 结束处理 ➔ 挂断收尾），支持在画布中平滑上下滚动；
  - 阶段内部自上而下串联，阶段之间通过流动指示线衔接，真实展现电信呼叫生命周期。
- **第二期（已架构并在界面预演）**：动态编排、分支条件与版本发布体系。
  - **合理卡片属性**：严格区分「核心固定动作（系统级防删）」与「可配置动作」；
  - **阶段扩展性**：明确在路由决策（ROUTE）与结束处理（END）阶段支持动态添加动作节点；
  - **if-else 多分支编排**：以按键或客户身份为判断条件，支持分流至 DID直达、业务系统接口回调、多维规则引擎及兜底组；
  - **版本控制与发布管理**：支持草稿与多历史版本切换（如 `v1.0.0 线上版` / `v1.1.0 草稿版`），支持发布前 Diff 差异审核与一键发布生效；
  - **交互式单步仿真测试机**：支持输入模拟主叫与模拟按键，单步推演验证分流正确性。

---

## 2. 第一期落地：纵向从上到下流转时序（Top-to-Bottom Pipeline）

```
[ 客户进线 / 坐席外呼 ]
         │
         ▼
┌──────────────────────────────────────────────────────────┐
│  阶段 1: 触发与应答阶段 (START / ANSWER)  [🔒 核心固定]    │
│  - 01: START (进线通道建立，解析 Caller 与 DID)           │
│  - 02: ANSWER (系统摘机应答，200 OK，建立 RTP 双通媒体)    │
│  - 03: PLAY_WELCOME (播放企业欢迎导航语音，可配置 TTS/WAV) │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  阶段 2: 路由决策与排队分流 (ROUTE & ACD) [✏️ 可编排·多分支] │
│  - READ_DTMF: 收号导航按键采集 (有效位 [1,2,3], 超时 5s)  │
│  - if-else 多分支矩阵:                                   │
│    ├── IF 按 1 ➔ DID 直达专席 (直通钱丁君 901001, <50ms)  │
│    ├── IF 按 2 ➔ 业务接口回调 (POST 运单匹配, 800ms 熔断) │
│    ├── IF 按 3 ➔ 规则引擎 (时段工作日 09:00-18:00+VIP插队) │
│    └── ELSE 兜底 ➔ 通用客服排队组 (零漏话兜底)              │
│  - QUEUE_ACD & DIAL_AGENT: 技能组排队分发，向坐席发起振铃 │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  阶段 3: 通话服务中阶段 (CONNECTED) [🔒 核心固定·✏️策略可配]│
│  - BRIDGE: 通道双通桥接 (FreeSWITCH uuid_bridge)          │
│  - RECORD_START: 16kHz 立体声双轨混音录音启动              │
│  - 支线动作: TRANSFER (通话中转接) / HANGUP agent (坐席挂机)│
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  阶段 4: 结束分支处理阶段 (END HANDLING) [✏️ 策略可配·双分支]│
│  ├── 正常结束 (Normal End):                               │
│  │   RECORD_STOP ➔ DTMF 评价采集 ➔ PLAY 播放感谢语        │
│  └── 异常结束 (Error End):                               │
│      PLAY 再见语音 ➔ 自动在回拨总池生成待办回拨工单        │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│  阶段 5: 挂断与收尾阶段 (HANGUP / END) [🔒 核心固定]       │
│  - HANGUP: 释放信令与媒体通道资源，回传 SIP BYE 信令       │
│  - END: 生成 CDR 话单与 timeline 轨迹，坐席状态恢复就绪   │
└──────────────────────────────────────────────────────────┘
```

---

## 3. 第二期模型设计：卡片分类、多分支与阶段扩展性

### 3.1 动作卡片属性分级 (Card Mutability Hierarchy)

为了兼顾**电信级系统的高可靠性（防止用户把关键信令节点删掉导致电话无法接通）**与**业务配置的灵活性**，卡片分为两大类：

| 动作类型 | 标识微标 | 典型节点 | 可操作权限 | 设计原则 |
| :--- | :--- | :--- | :--- | :--- |
| **核心固定动作** (System Built-in) | `🔒 核心固定` | `START`（通道建立）、`ANSWER`（摘机）、`BRIDGE`（桥接）、`HANGUP`（挂断）、`END`（收尾） | **不可删除、不可调换前后时序**；仅支持查看或配置部分基础超时参数 | 保护电信协议栈完整性，确保 Channel 资源生命周期严格闭环 |
| **业务可配置动作** (Configurable) | `✏️ 可配置` | `PLAY_WELCOME`（欢迎语）、`READ_DTMF`（按键采集）、`DID_DIRECT`（直达目标）、`HTTP_CALLBACK`（回调接口）、`RULE_ENGINE`（规则仲裁）、`QUEUE_ACD`（排队参数）、`RECORD_START/STOP`（录音策略）、`POST_SURVEY`（满意度评价） | **支持修改业务参数、支持更换音频/接口/时段、支持自定义 if-else 分支** | 贴合企业日常运营策略热调整 |

### 3.2 阶段扩展性规范（哪些阶段可以添加卡片）

在流程画布中，各阶段的扩展性具有明确的定义：
1. **阶段 1 (TRIGGER)**：固定接入阶段，不开放任意添加动作，保证呼入呼出应答时延在毫秒级。
2. **阶段 2 (ROUTE & ACD)**：**核心开放扩展阶段**！
   - 支持在按键采集后添加 **if-else 条件分支**；
   - 支持添加 `HTTP_CALLBACK`（如调用 CRM、物流中台获取用户画像）；
   - 支持添加 `AI_AGENT`（大模型语音机器人先做前置意图识别）；
   - 支持添加 `QUEUE_ACD`（指定多级排队与溢出策略）。
3. **阶段 3 (CONNECTED)**：支持添加通话中侧边辅助动作：
   - 插入 `AI_INSPECTION`（通话中双轨音频实时流式智能质检）；
   - 插入 `TRANSFER`（盲转、咨询转规则）。
4. **阶段 4 (END HANDLING)**：**开放扩展阶段**！
   - 支持在挂断前插入 `SMS_NOTIFY`（发送短信回执/问卷）；
   - 支持替换满意度评价节点为动态语音调研。
5. **阶段 5 (HANGUP / END)**：固定收尾阶段，由软交换内核强制驱动，保证数据 100% 写入数据库。

### 3.3 if-else 多分支编排模式规范 (以呼入按键分流为例)

在 Stage 2 (ROUTE) 中，针对 `READ_DTMF`（按键采集）输出的分支，系统定义了标准化分支数据模型：

```json
{
  "nodeId": "node-dtmf-01",
  "actionType": "READ_DTMF",
  "promptFile": "/prompts/welcome_menu.wav",
  "timeoutSeconds": 5,
  "maxRetry": 2,
  "branches": [
    {
      "condition": "DTMF_KEY == '1'",
      "branchName": "分支 1: DID 直达模式",
      "targetAction": "DID_DIRECT",
      "params": {
        "targetType": "AGENT",
        "agentId": "901001",
        "agentName": "钱丁君",
        "ringTimeout": 20
      }
    },
    {
      "condition": "DTMF_KEY == '2'",
      "branchName": "分支 2: 业务系统接口回调模式",
      "targetAction": "HTTP_CALLBACK",
      "params": {
        "url": "http://api.fleet.internal/api/v1/driver/hotline/match",
        "timeoutMs": 800,
        "fallbackGroupId": "DAY_GROUP_1"
      }
    },
    {
      "condition": "DTMF_KEY == '3'",
      "branchName": "分支 3: 规则引擎时段/VIP分流",
      "targetAction": "RULE_ENGINE",
      "params": {
        "timeRange": "09:00 - 18:00",
        "vipPriority": true,
        "blacklistBlock": true
      }
    },
    {
      "condition": "DEFAULT_OR_TIMEOUT",
      "branchName": "ELSE 默认兜底分支",
      "targetAction": "QUEUE_ACD",
      "params": {
        "groupId": "DEFAULT_GENERAL_GROUP",
        "desc": "用户超时未按键或输入非法按键时转默认通用客服组排队"
      }
    }
  ]
}
```

---

## 4. 第二期版本管理与发布体系 (Version Control & Publishing)

### 4.1 版本生命周期状态机

```
┌──────────────────┐    编辑修改     ┌───────────────────┐    发布确认 (Diff 检查通过)    ┌───────────────────┐
│ v1.0.0 (线上运行) │ ────────────> │ v1.1.0 (草稿编辑中) │ ───────────────────────────> │ v1.1.0 (线上激活)  │
└──────────────────┘                └───────────────────┘                              └─────────┬─────────┘
                                                                                                 │ 历史降级
                                                                                                 ▼
                                                                                       ┌───────────────────┐
                                                                                       │ v1.0.0 (历史归档)  │
                                                                                       └───────────────────┘
```

1. **版本标识与状态**：
   - `ACTIVE`：线上当前正在运行的生产版本（同一 Flow 同时仅能有 1 个 ACTIVE）。
   - `DRAFT`：草稿版本，运维或业务人员可以在不影响现网呼叫的前提下任意调整卡片参数、添加分支；
   - `ARCHIVED`：历史已归档版本，支持只读回溯，可一键快速回滚（Rollback）。
2. **发布前安全自检机制（Pre-flight Check）**：
   - 检查 1：**全路径有兜底**：任何 `if-else` 分支必须包含 `ELSE / TIMEOUT` 兜底路径；
   - 检查 2：**超时熔断保护**：所有 `HTTP_CALLBACK` 节点必须配置 `timeoutMs <= 1000` 并指派降级技能组；
   - 检查 3：**核心节点完整性**：START, ANSWER, HANGUP, END 不可缺失；
   - 检查 4：**FreeSWITCH ESL 兼容性**：Dialplan XML/Lua 模板语法校验通过。
3. **发布确认与 Diff 对比**：
   - 弹出模态框展示当前草稿相比线上版本的变动清单（如新增了哪个按键分支、修改了哪个接口 URL）；
   - 输入本次发布的版本号（如 `v1.1.0`）及变更说明（Release Notes）；
   - 确认后瞬间热推送至 FreeSWITCH 内存 Dialplan，无需重启软交换服务。

---

## 5. 二期数据库表结构设计 (DDL 规划)

为了全面支撑上述编排、多分支与版本历史能力，数据库引入版本管理表与图结构表：

```sql
-- 1. 流程主定义表 (扩展当前激活版本字段)
CREATE TABLE IF NOT EXISTS `ivr_flow_definition` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `flow_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '流程标识(如 FLOW-INBOUND)',
  `flow_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
  `call_type` VARCHAR(32) NOT NULL DEFAULT 'INBOUND' COMMENT '适用通话类型',
  `active_version` VARCHAR(32) NOT NULL DEFAULT 'v1.0.0' COMMENT '当前线上激活版本号',
  `description` VARCHAR(500) NULL COMMENT '业务描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IVR流程主定义表';

-- 2. 流程版本历史表 (存储版本快照与 DSL)
CREATE TABLE IF NOT EXISTS `ivr_flow_version` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `flow_code` VARCHAR(64) NOT NULL COMMENT '所属流程标识',
  `version_no` VARCHAR(32) NOT NULL COMMENT '版本号(如 v1.0.0, v1.1.0)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态(ACTIVE, DRAFT, ARCHIVED)',
  `flow_dsl_json` JSON NOT NULL COMMENT '完整流程图DSL(包含阶段、节点、分支条件完整JSON快照)',
  `release_note` VARCHAR(500) NULL COMMENT '版本发布更新说明',
  `publisher_id` VARCHAR(64) NULL COMMENT '发布人工号/姓名',
  `published_at` DATETIME NULL COMMENT '发布生效时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_flow_version` (`flow_code`, `version_no`),
  INDEX `idx_flow_status` (`flow_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IVR流程版本历史与快照表';
```

---

## 6. 前端落地文件与验证

- **前端核心实现**：`src/views/IvrFlowView.vue`
  - 彻底淘汰横向平铺，实现自上而下 5 阶段纵向时序流水线，支持平滑上下滑动；
  - 呈现 Stage 2 (ROUTE) 下 `READ_DTMF` ➔ 3 种分支（DID直达 / 业务接口回调 / 规则引擎）+ 1 个默认兜底组的 if-else 卡片网格；
  - 顶部增加版本切换器（v1.0.0 / v1.1.0 / v0.9.0）与版本状态徽章；
  - 动作库模态框（`showAddActionModal`）支持按路由、播报、控制、数据分类插入；
  - 发布上线模态框（`showPublishModal`）提供 Diff 审核与安全自检；
  - 仿真测试机（`showSimModal`）支持按键模拟（按1走DID、按2走接口、按3走规则、超时走兜底），输出清晰阶段日志。
- **构建状态**：`npm run build` 打包验证 100% 成功，0 错误。
