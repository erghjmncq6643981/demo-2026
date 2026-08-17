# AI 响应验收与快速修正待办

> 交接目标：下一次 Codex 会话根据真实模型调用记录，逐场景判断 AI 返回是否满足业务契约，定位问题属于提示词、解析器、业务校验还是模型兼容层，并以最小改动完成修正和回归。

## 0. 开始前必须确认

- [ ] 阅读根目录 `AGENTS.md`，同时检查前后端工作区，禁止覆盖用户未提交改动。
- [ ] 执行已有库补丁 `src/main/resources/db/95_ai_invocation_scene_mysql.sql`；新库使用更新后的 `init/00_ai_agent_init_mysql.sql`。
- [ ] 配置一个已启用的模型，但不要把真实 API Key 写进源码、文档或 SQL。
- [ ] 用测试账号登录，通过真实业务接口触发调用，不要直接绕过 Service 调模型。
- [ ] 每个场景至少收集 3 条成功响应；结构化场景额外收集 1 条失败或边界样本。

## 1. 场景清单与响应契约

枚举唯一来源：`src/main/java/com/chandler/learning/agent/domain/enums/AiInvocationScene.java`。

| `invocation_scene_code` | 触发入口 | Prompt / 解析位置 | 最低预期 |
| --- | --- | --- | --- |
| `general_chat` | `POST /api/v1/ai/agents/chat` | `AiChatService` | 非空、与用户问题相关的文本；若调用方声明其他场景，不应落到这里 |
| `vocabulary_follow_up` | 卡片学习页的 AI 追问 | `AiChatService`，复用当前词汇学习会话 | 回答围绕当前词或用户追问，不应输出无关词卡，也不应创建新的会话 |
| `vocabulary_card_single` | 单词卡片学习查词 | `english_vocab_json` / `EnglishVocabularyStudyService.extractJson` | 合法 JSON；包含 term、definitions、examples、collocations、memory_tips；例句有中文翻译；相关词仅为同义词、反义词、词族 |
| `vocabulary_card_batch` | 场景核心词缺卡时批量生成 | `english_vocab_cards_batch_json` / `VocabularyCardBatchService.parseCards` | 根字段 cards；输入词一词一项且无重复；每项满足单词词卡契约；不得漏词或混入未请求词 |
| `article_study_material` | 从个人单词本生成文章学习材料 | `english_article_study_json` / `ArticleStudyService.extractJson` | 合法 JSON；文章自然覆盖所选词；中英译文对应；词汇、语法和练习结构完整 |
| `vocabulary_scene_unit` | 学习计划生成下一个词汇大挑战场景 | `english_vocabulary_scene_plan_json` / `LearningPlanService.parseScene` | 合法 JSON；核心词 8-20 个且来自候选词表；场景文章连贯；掌握要求合理；含义四选一恰好 4 项；扩展名词与场景相关 |

`LearningScene` 管会话复用边界，`AiInvocationScene` 管单次调用任务。不要把两者合并，也不要按一次请求新建一个学习场景会话。

## 2. 提取最近真实响应

先按场景看成功率、Token 和延迟：

```sql
SELECT invocation_scene_code,
       COUNT(*) AS call_count,
       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS transport_success_count,
       ROUND(AVG(total_tokens), 0) AS avg_tokens,
       ROUND(AVG(latency_ms), 0) AS avg_latency_ms
FROM ai_model_call_record
WHERE deleted = 0
GROUP BY invocation_scene_code
ORDER BY invocation_scene_code;
```

提取某场景最近 20 条原始供应商响应：

```sql
SELECT id,
       invocation_scene_code,
       session_id,
       agent_code,
       provider,
       model_name,
       success,
       error_message,
       total_tokens,
       latency_ms,
       request_json,
       response_json,
       create_time
FROM ai_model_call_record
WHERE deleted = 0
  AND invocation_scene_code = 'vocabulary_scene_unit'
ORDER BY create_time DESC
LIMIT 20;
```

注意：`success = 1` 只表示模型 HTTP 调用和供应商响应解析成功，不代表业务 JSON 已通过后续 Service 校验。还要结合接口错误、业务表的 `raw_content` / `parsed_json`、批量任务的 `error_message` 判断契约是否成功。

## 3. 每条响应的分析步骤

- [ ] 还原输入：查看 `request_json.messages`、模板编码、模板变量、模型和会话历史。
- [ ] 提取输出：从 `response_json.choices[0].message.content` 取得模型正文；兼容供应商时同时检查 `choices[0].text`。
- [ ] 检查语法：结构化场景必须是可解析 JSON；记录代码块包裹、前后解释文字、截断、非法转义等问题。
- [ ] 检查结构：对照上表和枚举的 `requiredRootFields`；继续检查数组数量、嵌套字段、字段类型和唯一性。
- [ ] 检查业务：验证核心词来源、学习目标匹配度、场景相关性、译文准确度、题目唯一正确答案以及拼写答案可接受变体。
- [ ] 检查教学质量：文章是否自然串联词汇，是否避免机械堆词；20-40 岁学习者能否在一次场景中理解、检查并复习。
- [ ] 给样本标注 `PASS`、`PROMPT_GAP`、`PARSER_GAP`、`VALIDATION_GAP`、`MODEL_COMPATIBILITY` 或 `SESSION_CONTEXT`。

## 4. 快速定位与最小修正

| 现象 | 首选修正位置 | 修正规则 |
| --- | --- | --- |
| 字段稳定缺失、数量不对、内容偏题 | 对应 `ai_prompt_template` | 先收紧输出约束和示例；已有库新增下一编号增量 SQL，不要改写已经执行的补丁意图 |
| JSON 被代码块或少量说明包裹 | 对应 Service 的 JSON 提取方法 | 保留严格解析优先，再做一次边界明确的兼容提取；不得用大范围字符串截断掩盖坏响应 |
| 模型使用可接受的同义字段名 | 对应 Service 的 `node` / `text` 字段别名 | 只加入有真实样本证据的别名，并补单元测试 |
| 核心词越界、四选一非 4 项、答案不在选项中 | `LearningPlanService` 校验逻辑和场景 Prompt | 业务不变量继续硬校验；Prompt 用于降低失败率，不能替代服务端校验 |
| 批量词卡漏词或重复 | `VocabularyCardBatchService.parseCards` | 按 normalized term 对齐输入输出；只重试失败项，不重跑已成功词卡 |
| 某供应商响应 envelope 不兼容 | `OpenAiCompatibleModelClient.parseResponse` | 仅修供应商协议层，不把业务 JSON 规则放进模型客户端 |
| 后续场景重复或被旧上下文污染 | `AiChatService` / `AiChatSessionService` / `LearningPlanService` | 一个学习计划复用一个会话；检查 session 所属用户、场景和已完成场景变量，不要按请求滥建会话 |

修正顺序：先确认可重复样本，再改最靠近根因的一层；每次只处理一种失败标签，保留失败原文作为测试夹具并脱敏。

## 5. 回归验证

- [ ] 为每个被修问题增加最小单元测试，覆盖失败样本和正常样本。
- [ ] 运行 `mvn -q test` 和 `mvn -q -DskipTests compile`。
- [ ] 用同一输入、同一模型重新调用至少 3 次，比较修正前后的契约通过率，不以单次成功下结论。
- [ ] 确认 `ai_model_call_record.invocation_scene_code` 均正确，不应由业务服务发起的请求落到 `general_chat`。
- [ ] 确认单词词卡的个人快照未被公共缓存后续更新覆盖，相关词没有混入 collocations。
- [ ] 确认词汇大挑战核心词学习闭环仍为：文章 -> 核心词 -> 仅单词清单 -> 含义/拼写检查 -> 逐词进度。
- [ ] 把结论写入 `docs/ai-response-analysis-report.md`，至少包含样本 ID、场景、模型、失败标签、根因、修改文件、测试和修正前后结果。

## 6. 完成标准

- [ ] 六个枚举场景均有真实样本结论，没有“看起来应该可以”的未验证判断。
- [ ] 所有结构化场景的 JSON 可解析率和业务契约通过率分别统计。
- [ ] 修正没有放宽核心业务不变量，没有吞掉解析异常，也没有引入真实密钥或未脱敏响应。
- [ ] 后端测试与编译通过；若涉及前端展示，再执行变更模块的 `node --check` 和桌面/移动端浏览器验证。
