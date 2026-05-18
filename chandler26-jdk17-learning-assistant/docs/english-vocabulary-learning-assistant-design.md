# 英语词汇学习助手设计文档

## 1. 背景与目标

本项目计划建设一个面向个人学习的学习助手软件。第一阶段聚焦英语词汇学习，通过接入 Kimi、元宝、DeepSeek、豆包等大模型 API，围绕单词、短语、例句、语境、测试题等学习问题生成结构化内容，并将这些内容沉淀到系统数据库中，支持后续复习、检索、测验和学习路径推荐。

当前仓库是基于 JDK 17、Spring Boot 3、MyBatis Plus、MySQL、SpringDoc 的后端模板工程。第一阶段建议优先建设后端能力：词汇数据建模、AI 内容生成、模板管理、结构化解析、持久化与基础 API。前端可以在后续阶段接入 Web、移动端或小程序。

## 2. 产品定位

### 2.1 核心用户

- 希望系统学习英语词汇的个人学习者。
- 需要把零散单词整理成可复习知识库的学习者。
- 希望借助 AI 获取解释、例句、词根词缀、搭配、同近反义词、测试题的学习者。

### 2.2 第一阶段范围

第一阶段只做英语词汇学习，不做完整的英语听说读写训练。

范围内：

- 单词或短语的基础信息生成。
- 词义、音标、词性、例句、常见搭配、同义词、反义词、词根词缀、记忆提示生成。
- 与单词相关的选择题、填空题、翻译题生成。
- AI 生成内容结构化解析和入库。
- 生成记录、解析记录和错误记录留痕。
- 支持多个 AI 供应商的配置、调用和切换。

范围外：

- 语音识别、口语评分。
- 作文批改。
- 视频课程。
- 社交学习。
- 完整的间隔重复算法优化。

## 3. 总体架构

系统采用分层架构，后端作为核心服务，AI 供应商作为外部依赖。

```text
客户端/管理端
    |
    v
Spring Boot API 层
    |
    v
业务服务层
    |-- 词汇服务
    |-- 学习内容服务
    |-- 题目服务
    |-- AI 生成任务服务
    |-- 模板服务
    |
    v
基础能力层
    |-- AI Provider 适配器
    |-- Prompt 模板渲染
    |-- 结构化响应解析
    |-- 内容校验与归一化
    |-- 失败重试与日志
    |
    v
MySQL 数据库
```

### 3.1 后端模块建议

建议在现有包名下逐步从 `example` 模板迁移到真实业务包，例如：

```text
com.chandler.instance.client.learning
    ├── ai                  AI 供应商适配、请求响应、解析
    ├── vocabulary          词汇主数据
    ├── content             释义、例句、搭配、记忆内容
    ├── quiz                测验题目
    ├── prompt              模板管理
    ├── task                生成任务与异步处理
    ├── common              通用返回、异常、枚举
    └── config              配置
```

## 4. 核心业务流程

### 4.1 单词内容生成流程

```text
用户输入单词/短语
    |
    v
检查本地词汇库是否存在
    |
    |-- 已存在：返回已有内容，可选择重新生成
    |
    |-- 不存在：
          创建 AI 生成任务
          选择供应商和 Prompt 模板
          调用 AI API
          获取原始文本响应
          解析为标准 JSON 结构
          校验字段完整性和内容质量
          保存词汇、释义、例句、搭配、题目
          返回结构化学习卡片
```

### 4.2 AI 内容入库流程

AI 响应不应直接作为业务数据使用。建议保留三层数据：

1. 原始响应：完整保存供应商返回内容，便于排查问题和重新解析。
2. 解析结果：保存解析后的标准 JSON，便于对比和审计。
3. 业务数据：拆分到词汇、释义、例句、题目等正式业务表。

这样可以避免 AI 输出格式偶发偏移时直接污染主数据。

## 5. AI 接入设计

### 5.1 Provider 抽象

不同供应商的鉴权、接口路径、模型名称、响应格式不同，但业务层只关心“给定 prompt 后得到文本结果”。建议定义统一接口：

```java
public interface AiProviderClient {
    AiProvider provider();

    AiTextResponse generate(AiTextRequest request);
}
```

核心请求对象：

```java
public class AiTextRequest {
    private String model;
    private String systemPrompt;
    private String userPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> metadata;
}
```

核心响应对象：

```java
public class AiTextResponse {
    private AiProvider provider;
    private String model;
    private String content;
    private String requestId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
}
```

### 5.2 供应商枚举

```java
public enum AiProvider {
    KIMI,
    YUANBAO,
    DEEPSEEK,
    DOUBAO
}
```

### 5.3 配置示例

建议通过 `application.yaml` 加环境变量配置，不把密钥提交到仓库。

```yaml
learning:
  ai:
    default-provider: DEEPSEEK
    providers:
      deepseek:
        enabled: true
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-chat
      kimi:
        enabled: false
        base-url: https://api.moonshot.cn
        api-key: ${KIMI_API_KEY:}
        model: moonshot-v1-8k
      doubao:
        enabled: false
        base-url: ${DOUBAO_BASE_URL:}
        api-key: ${DOUBAO_API_KEY:}
        model: ${DOUBAO_MODEL:}
      yuanbao:
        enabled: false
        base-url: ${YUANBAO_BASE_URL:}
        api-key: ${YUANBAO_API_KEY:}
        model: ${YUANBAO_MODEL:}
```

供应商实际接口参数可能变化，接入时应以各自官方文档为准。业务设计上保持适配器隔离，避免供应商差异进入核心业务层。

## 6. Prompt 模板设计

### 6.1 模板原则

- 要求 AI 返回严格 JSON，不返回 Markdown 包裹。
- 模板中显式定义字段、类型、数量限制和语言要求。
- 每个任务只生成一种明确结构，避免一个 prompt 里混合过多目标。
- 保留模板版本，后续生成内容可以追溯到使用了哪个模板。

### 6.2 词汇学习卡片模板

系统提示词：

```text
你是一个严谨的英语词汇学习助手。你只输出合法 JSON，不要输出 Markdown、解释文字或代码块。
如果输入不是有效英语单词或短语，也要返回 JSON，并在 is_valid 字段中标记 false。
```

用户提示词：

```text
请为英语词汇「{{term}}」生成学习卡片。

输出 JSON 结构必须符合以下字段：
{
  "term": "string",
  "is_valid": true,
  "language": "en",
  "phonetic": {
    "uk": "string",
    "us": "string"
  },
  "definitions": [
    {
      "part_of_speech": "noun|verb|adjective|adverb|phrase|other",
      "meaning_cn": "中文释义",
      "meaning_en": "simple English definition",
      "frequency": 1
    }
  ],
  "examples": [
    {
      "sentence": "English sentence",
      "translation_cn": "中文翻译",
      "difficulty": "A1|A2|B1|B2|C1|C2"
    }
  ],
  "collocations": [
    {
      "phrase": "string",
      "meaning_cn": "string"
    }
  ],
  "synonyms": ["string"],
  "antonyms": ["string"],
  "word_family": [
    {
      "term": "string",
      "part_of_speech": "string",
      "meaning_cn": "string"
    }
  ],
  "memory_tips": [
    {
      "type": "root|association|scenario|contrast",
      "content": "string"
    }
  ]
}

要求：
- definitions 生成 1 到 4 条，按常用程度排序。
- examples 生成 3 条，难度从低到高。
- collocations 生成 3 到 6 条。
- 中文解释简洁准确。
- 不要编造不存在的词形变化。
```

### 6.3 测验题生成模板

```text
请基于英语词汇「{{term}}」生成 5 道词汇练习题。

只输出合法 JSON：
{
  "term": "string",
  "questions": [
    {
      "type": "choice|blank|translation",
      "stem": "题干",
      "options": ["A", "B", "C", "D"],
      "answer": "正确答案",
      "analysis": "中文解析",
      "difficulty": "easy|medium|hard"
    }
  ]
}

要求：
- 至少 3 道选择题。
- 选项必须有迷惑性，但不能有多个正确答案。
- blank 类型题目中用 ____ 表示空格。
- translation 类型题目答案可以是一个短语或句子。
```

## 7. 结构化解析与校验

### 7.1 解析策略

AI 返回内容可能出现以下问题：

- JSON 外包裹 Markdown 代码块。
- 字段缺失。
- 字段类型错误。
- 中英文混杂不符合要求。
- 返回了额外解释文本。
- 数组数量不符合要求。

建议解析流程：

```text
原始文本
    |
    v
清理 Markdown 包裹和前后噪声
    |
    v
提取第一个完整 JSON 对象
    |
    v
Jackson 反序列化为 DTO
    |
    v
Bean Validation 校验必填字段
    |
    v
业务校验：数量、枚举值、term 一致性
    |
    v
归一化：trim、大小写、去重
```

### 7.2 失败处理

解析失败时不应直接丢弃。建议：

- 保存原始响应。
- 记录失败原因和异常堆栈摘要。
- 任务状态置为 `PARSE_FAILED`。
- 支持手动重新解析。
- 支持使用更严格的修复模板，让 AI 把原始响应转换成目标 JSON。

## 8. 数据模型设计

### 8.1 主要实体

| 实体 | 说明 |
| --- | --- |
| `vocabulary_term` | 词汇主表 |
| `vocabulary_definition` | 词义表 |
| `vocabulary_example` | 例句表 |
| `vocabulary_collocation` | 搭配表 |
| `vocabulary_relation` | 同义词、反义词、派生词关系 |
| `memory_tip` | 记忆提示 |
| `quiz_question` | 练习题 |
| `prompt_template` | Prompt 模板 |
| `ai_generation_task` | AI 生成任务 |
| `ai_generation_record` | AI 请求响应记录 |

### 8.2 表结构草案

#### vocabulary_term

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term` | varchar(128) | 单词或短语 |
| `normalized_term` | varchar(128) | 归一化词条，用于唯一索引 |
| `language` | varchar(16) | 语言，第一阶段固定 en |
| `uk_phonetic` | varchar(128) | 英式音标 |
| `us_phonetic` | varchar(128) | 美式音标 |
| `valid_status` | varchar(32) | VALID、INVALID、PENDING |
| `source_type` | varchar(32) | AI、MANUAL、IMPORT |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议索引：

- `uk_normalized_term` 唯一索引：`normalized_term`

#### vocabulary_definition

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `part_of_speech` | varchar(32) | 词性 |
| `meaning_cn` | varchar(512) | 中文释义 |
| `meaning_en` | varchar(1024) | 英文释义 |
| `frequency_rank` | int | 常用程度排序 |
| `created_at` | datetime | 创建时间 |

#### vocabulary_example

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `definition_id` | bigint | 可选，关联具体释义 |
| `sentence` | varchar(1024) | 英文例句 |
| `translation_cn` | varchar(1024) | 中文翻译 |
| `difficulty` | varchar(16) | CEFR 难度 |
| `created_at` | datetime | 创建时间 |

#### vocabulary_collocation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `phrase` | varchar(256) | 搭配短语 |
| `meaning_cn` | varchar(512) | 中文释义 |
| `created_at` | datetime | 创建时间 |

#### vocabulary_relation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `relation_type` | varchar(32) | SYNONYM、ANTONYM、WORD_FAMILY |
| `related_term` | varchar(128) | 相关词 |
| `part_of_speech` | varchar(32) | 可选词性 |
| `meaning_cn` | varchar(512) | 可选中文释义 |
| `created_at` | datetime | 创建时间 |

#### memory_tip

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `tip_type` | varchar(32) | root、association、scenario、contrast |
| `content` | varchar(1024) | 记忆内容 |
| `created_at` | datetime | 创建时间 |

#### quiz_question

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `term_id` | bigint | 词汇 ID |
| `question_type` | varchar(32) | choice、blank、translation |
| `stem` | varchar(1024) | 题干 |
| `options_json` | json | 选项 |
| `answer` | varchar(1024) | 答案 |
| `analysis` | varchar(1024) | 解析 |
| `difficulty` | varchar(32) | easy、medium、hard |
| `created_at` | datetime | 创建时间 |

#### prompt_template

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `template_code` | varchar(64) | 模板编码 |
| `template_name` | varchar(128) | 模板名称 |
| `template_version` | int | 版本 |
| `task_type` | varchar(64) | VOCABULARY_CARD、QUIZ_GENERATION |
| `system_prompt` | text | 系统提示词 |
| `user_prompt` | text | 用户提示词 |
| `response_schema` | json | 期望响应结构 |
| `enabled` | tinyint | 是否启用 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

#### ai_generation_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `task_type` | varchar(64) | 任务类型 |
| `business_key` | varchar(128) | 业务键，例如 term |
| `provider` | varchar(32) | AI 供应商 |
| `model` | varchar(128) | 模型 |
| `template_id` | bigint | 模板 ID |
| `status` | varchar(32) | PENDING、RUNNING、SUCCESS、API_FAILED、PARSE_FAILED |
| `retry_count` | int | 重试次数 |
| `error_message` | varchar(2048) | 错误摘要 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

#### ai_generation_record

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `task_id` | bigint | 任务 ID |
| `provider` | varchar(32) | AI 供应商 |
| `model` | varchar(128) | 模型 |
| `request_json` | json | 请求内容 |
| `raw_response` | mediumtext | 原始响应 |
| `parsed_json` | json | 解析后的 JSON |
| `prompt_tokens` | int | 输入 token |
| `completion_tokens` | int | 输出 token |
| `latency_ms` | bigint | 耗时 |
| `success` | tinyint | 是否成功 |
| `error_message` | varchar(2048) | 错误摘要 |
| `created_at` | datetime | 创建时间 |

## 9. API 设计草案

### 9.1 词汇接口

#### 查询词汇

```http
GET /api/v1/vocabularies/{term}
```

返回词汇学习卡片。如果本地不存在，可通过参数决定是否触发生成：

```http
GET /api/v1/vocabularies/abandon?generateIfAbsent=true
```

#### 创建生成任务

```http
POST /api/v1/vocabularies/generation-tasks
Content-Type: application/json

{
  "term": "abandon",
  "taskTypes": ["VOCABULARY_CARD", "QUIZ_GENERATION"],
  "provider": "DEEPSEEK"
}
```

#### 查询生成任务

```http
GET /api/v1/ai-generation-tasks/{taskId}
```

#### 重新生成词汇内容

```http
POST /api/v1/vocabularies/{term}/regenerate
```

### 9.2 模板接口

```http
GET /api/v1/prompt-templates
POST /api/v1/prompt-templates
POST /api/v1/prompt-templates/{id}/enable
POST /api/v1/prompt-templates/{id}/disable
```

第一阶段可以先不做完整模板管理页面，模板可通过初始化 SQL 或后端配置写入。

## 10. 应用层设计

### 10.1 VocabularyService

职责：

- 查询词汇学习卡片。
- 创建或更新词汇主数据。
- 聚合返回释义、例句、搭配、关系、记忆提示、题目。
- 控制重复生成策略。

### 10.2 AiGenerationService

职责：

- 创建生成任务。
- 执行任务。
- 调用模板渲染。
- 调用 AI Provider。
- 保存请求、响应和解析结果。
- 协调解析结果入库。

### 10.3 PromptTemplateService

职责：

- 查询可用模板。
- 按任务类型选择模板。
- 渲染模板变量。
- 管理模板版本。

### 10.4 AiResponseParser

职责：

- 清理模型返回文本。
- 提取 JSON。
- 反序列化到目标 DTO。
- 执行字段校验。
- 返回标准解析结果。

### 10.5 Provider Adapter

职责：

- 各供应商鉴权。
- HTTP 请求封装。
- 响应字段映射。
- 错误码转换。

建议先实现一个供应商，例如 DeepSeek，再扩展 Kimi、豆包、元宝。这样可以先跑通业务闭环。

## 11. 任务执行模式

第一阶段建议支持同步和异步两种模式：

### 11.1 同步模式

适合开发调试或低频调用：

```text
请求进入 -> 调用 AI -> 解析 -> 入库 -> 返回结果
```

优点是实现简单；缺点是接口耗时较长，供应商超时时用户体验差。

### 11.2 异步模式

适合正式使用：

```text
请求进入 -> 创建任务 -> 立即返回 taskId
后台线程执行 -> 前端轮询任务状态或后续接 WebSocket/SSE
```

第一阶段可使用 Spring `@Async` 或简单线程池，后续任务量上来后再引入消息队列。

## 12. 质量控制

### 12.1 内容质量

- 单词必须和请求词条一致，允许大小写归一化。
- 词性必须在枚举范围内。
- 例句必须包含目标词或其合理变形。
- 选择题必须有且只有一个正确答案。
- 同义词、反义词不直接入主词库，除非用户或任务明确生成该词。

### 12.2 防重复策略

- `normalized_term` 唯一。
- 同一 `term_id` 下例句按 `sentence` 去重。
- 搭配按 `phrase` 去重。
- 关系按 `relation_type + related_term` 去重。

### 12.3 可追溯性

每条 AI 生成业务数据建议保留来源字段：

- `source_type`
- `source_task_id`
- `source_record_id`

若第一阶段想减少字段数量，至少保留任务和记录表，方便排查。

## 13. 安全与成本控制

### 13.1 API Key 管理

- API Key 只通过环境变量或本地未提交配置提供。
- 不在日志中打印完整请求头。
- 请求记录中不要保存 Authorization。

### 13.2 调用频率

- 对同一个词条设置生成冷却时间。
- 同一供应商设置超时和重试上限。
- 支持手动切换供应商。
- 后续可以增加每日 token 预算。

### 13.3 输入安全

- 限制 term 长度，例如 128 字符。
- 只允许合理字符：英文字母、空格、连字符、撇号。
- 对 Prompt 变量做转义，降低提示词注入影响。

## 14. 开发里程碑

### 阶段 1：后端基础闭环

- 建立真实业务包结构。
- 创建词汇、释义、例句、搭配、题目、AI 任务相关表。
- 实现 Prompt 模板常量或模板表初始化。
- 实现一个 AI Provider 适配器。
- 实现词汇学习卡片生成、解析、入库。
- 提供 Swagger 可调试 API。

### 阶段 2：内容扩展

- 增加测验题生成。
- 增加多供应商适配。
- 增加重新生成、重新解析能力。
- 增加内容质量校验和去重。

### 阶段 3：学习体验

- 增加学习记录。
- 增加收藏、掌握状态、复习计划。
- 增加错题记录。
- 增加简单前端页面。

### 阶段 4：个性化与规模化

- 根据用户水平调整解释和例句难度。
- 引入间隔重复算法。
- 支持批量导入词表。
- 支持任务队列、限流、成本统计。

## 15. 推荐第一版接口闭环

第一版最小可用功能建议只做 4 个接口：

```http
POST /api/v1/vocabularies/generate
GET  /api/v1/vocabularies/{term}
POST /api/v1/vocabularies/{term}/regenerate
GET  /api/v1/ai-generation-tasks/{taskId}
```

第一版最小表集合：

- `vocabulary_term`
- `vocabulary_definition`
- `vocabulary_example`
- `vocabulary_collocation`
- `vocabulary_relation`
- `memory_tip`
- `quiz_question`
- `prompt_template`
- `ai_generation_task`
- `ai_generation_record`

第一版最小技术闭环：

```text
Spring Boot Controller
    -> VocabularyService
    -> AiGenerationService
    -> DeepSeekProviderClient
    -> AiResponseParser
    -> MyBatis Plus Mapper
    -> MySQL
```

## 16. 后续待确认问题

- 第一阶段是否只支持单用户，还是从一开始引入用户体系。
- AI 供应商优先接入哪一个，建议先选接口兼容性较好的一个跑通闭环。
- 是否需要前端页面，还是先用 Swagger/Postman 调试。
- 数据库迁移工具是否引入 Flyway 或 Liquibase。
- AI 生成任务是否第一版就异步化。
- 是否需要支持批量导入 CET-4、CET-6、考研、雅思、托福词表。

