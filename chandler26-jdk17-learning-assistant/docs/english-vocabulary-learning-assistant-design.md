# 英语词汇学习助手设计文档

## 1. 背景与目标

本项目建设一个面向个人学习的英语词汇学习助手。第一阶段聚焦“输入英语单词或短语 -> 调用 AI 生成结构化学习卡片 -> 数据库缓存 -> 加入词书 -> 按艾宾浩斯复习计划复习”的闭环。

系统已接入 OpenAI-compatible 风格的大模型调用方式，可配置 Kimi、DeepSeek、豆包、元宝等供应商。当前实现优先支持 DeepSeek/Kimi 这类兼容 `/chat/completions` 的模型接口。

核心目标：

- 避免重复调用 AI：同一归一化词条优先读取数据库缓存。
- 将 AI 返回内容结构化：保存原始文本、解析 JSON、标签和关联词。
- 支持用户体系：登录后拥有自己的词书和复习计划。
- 支持学习体验：前端拆分为登录、个人信息、单词本、学习、复习几个主区域。
- 支持后续扩展：保留 Agent、Prompt 模板、多供应商、系统日志和复习记录。

## 2. 当前实现范围

已实现：

- AI Agent 配置、Prompt 模板、同步对话和模型调用记录。
- 英语词汇学习缓存接口。
- 用户注册、登录、退出登录、Token 鉴权。
- 词书创建、词书列表、词条加入词书、词条列表。
- 单词本独立菜单：按词表查看单词，按熟悉、模糊、遗忘筛选，支持修改状态和从词表删除。
- 学习/复习共享 Markdown 笔记：同一词书词条的笔记在学习页和复习页实时一致。
- 模型配置管理：个人信息页可新增、编辑、启用/禁用模型配置，并设置默认和优先级。
- 词汇标签：词性、含义主题、难度、搭配、词族。
- 词汇关联：同义词、反义词、词族、搭配、共享标签相近词。
- 艾宾浩斯复习计划：待复习队列、复习提交、下一次复习时间。
- 前端产品化页面：独立登录页；登录后个人信息、单词本、学习、复习四功能区。
- Raw JSON 移入个人信息页的系统日志区域。

暂未实现：

- 批量导入词表。
- 完整测验题生成与错题本。
- 更精细的间隔重复算法参数配置。
- Spring Security/JWT 标准化认证。
- Flyway/Liquibase 自动迁移。

## 3. 系统架构

```text
前端静态应用
    |
    | HTTP + Bearer Token
    v
Spring Boot API
    |
    |-- AI Agent / Prompt / Chat / Model Config
    |-- Vocabulary Study Cache
    |-- Auth / User Token
    |-- Wordbook / Review
    |-- Vocabulary Tags / Relations
    |
    v
MyBatis Plus Mapper
    |
    v
MySQL
    |
    v
AI Provider API
```

后端项目：

```text
/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant
```

前端项目：

```text
/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
```

## 4. 前端产品设计

### 4.1 信息架构

未登录：

- 独立登录界面。
- 包含后端地址、用户名、密码、昵称、登录、注册。
- 界面定位强调“把每一次查词变成可复习的知识资产”。

登录后：

- 左侧导航：个人信息、单词本、学习、复习。
- 顶部区域：当前功能标题、当前词书选择、刷新词书、刷新 Agent。
- 个人信息：
  - 账户概览。
  - 词书数量、单词数量、待复习数量。
  - 单词表管理：新建/编辑词表、设置默认词表。
  - 模型配置：新增/编辑模型、配置 API Key 和 Base URL、启停、默认模型、优先级。
  - Agent 管理：Agent、Prompt 模板、默认发音、强制刷新、继续追问。
  - 系统日志：AI 调用、缓存命中、追问、错误、复习提交等。
  - Raw JSON：显示最近一次 AI 结构化结果或追问原文。
- 单词本：
  - 选择当前词表后查看单词。
  - 支持按状态筛选：熟悉、模糊、遗忘。
  - 支持修改单词状态。
  - 支持从当前词表删除单词。
  - 支持查看并编辑该词条的 Markdown 笔记。
- 学习：
  - 单词/短语输入。
  - 学习按钮后可选择 AI 模型配置。
  - 英语学习卡片：单词、音标、释义、例句、搭配、记忆提示。
  - 标签和相关单词。
  - 加入当前词书。
  - Markdown 学习笔记，和复习页笔记共用同一 `learning_wordbook_entry.note`。
  - 单词/例句发音。
- 复习：
  - 当前词书的待复习队列。
  - 复习卡片。
  - 复习结果提交：忘记、模糊、记住。
  - Markdown 复习笔记，和学习页共享同一数据来源。

### 4.2 视觉风格

前端风格参考 `qwerty-learner`：

- 深色学习空间。
- 学习任务居中，单词卡片是页面视觉焦点。
- 克制的面板、轻量阴影、圆角控制在 12px 左右。
- 使用清晰的学习状态标签，而不是复杂后台表格。
- 导航简洁，主功能只保留个人信息、单词本、学习、复习。

### 4.3 前端文件

| 文件 | 说明 |
| --- | --- |
| `public/index.html` | 页面结构，包含登录页和四功能区应用壳 |
| `public/app.js` | 状态管理、接口调用、学习/词书/复习交互 |
| `public/styles.css` | 产品化样式，参考 qwerty-learner 的学习体验 |
| `server.mjs` | 零依赖静态文件服务 |

启动：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
/usr/local/bin/node server.mjs
```

访问：

```text
http://127.0.0.1:5173
```

设计预览模式：

```text
http://127.0.0.1:5173/?preview=1
```

说明：`preview=1` 只用于无后端或未登录时查看产品化界面，会在浏览器内注入模拟用户、词书、复习任务和词汇学习卡片，不影响真实登录和接口调用流程。

## 5. AI Agent 设计

### 5.1 模型供应商配置

配置位置：

```text
src/main/resources/application.yaml
config/application-local.yaml
```

密钥只允许放在环境变量或本地未提交配置中，不能写入仓库。

```yaml
learning:
  ai:
    default-provider: deepseek
    providers:
      deepseek:
        enabled: true
        base-url: https://api.deepseek.com
        chat-path: /chat/completions
        api-key: ${DEEPSEEK_API_KEY:}
        default-model: deepseek-chat
      kimi:
        enabled: true
        base-url: https://api.moonshot.cn
        chat-path: /v1/chat/completions
        api-key: ${KIMI_API_KEY:}
        default-model: moonshot-v1-8k
```

运行时模型配置优先级：

1. 学习页明确选择的 `modelConfigId`。
2. 数据库 `ai_model_config` 中启用且标记默认的配置。
3. 数据库 `ai_model_config` 中启用且优先级最高的配置。
4. `application.yaml` 中的 `learning.ai.providers` 静态配置。

前端个人信息页支持维护数据库模型配置，包括：

- `name`
- `provider`
- `model_name`
- `base_url`
- `chat_path`
- `api_key`
- `enabled`
- `is_default`
- `sequence`

### 5.2 Prompt 模板

当前词汇学习卡片模板要求 AI 返回 JSON，核心字段包括：

- `term`
- `is_valid`
- `language`
- `phonetic.uk`
- `phonetic.us`
- `definitions`
- `examples`
- `collocations`
- `synonyms`
- `antonyms`
- `word_family`
- `memory_tips`

结构约束：

- `definitions` 每条包含 `part_of_speech`、`meaning`、`english`。
- `collocations` 使用对象数组，每条包含 `phrase`、`meaning`，用于展示搭配含义。
- `synonyms`、`antonyms`、`word_family` 使用对象数组，每条包含 `word`、`part_of_speech`、`meaning`，用于展示相关词核心词性和核心含义。
- 如果用户输入疑似拼写错误，AI 可在 `term` 中输出判断后的标准单词；系统也提供缓存层面的最匹配词查询。

前端和后端都兼容常见字段变体，例如：

- `meaning` / `meaning_cn`
- `english` / `meaning_en`
- `translation` / `translation_cn`
- `part_of_speech` / `pos`
- 字符串数组或对象数组形式的 `collocations`
- 字符串数组或对象数组形式的 `synonyms`、`antonyms`、`word_family`

## 6. 后端模块

### 6.1 AI Agent 模块

主要文件：

- `controller/AiAgentController.java`
- `controller/AiPromptTemplateController.java`
- `controller/AiChatSessionController.java`
- `controller/AiModelConfigController.java`
- `service/AiChatService.java`
- `service/AiModelConfigService.java`
- `support/OpenAiCompatibleModelClient.java`

能力：

- Agent 管理。
- Prompt 模板管理。
- 模型配置管理：新增、编辑、启用/禁用、默认、优先级。
- Agent Chat。
- 会话和消息保存。
- 模型调用记录。

模型配置接口：

```http
GET    /api/v1/ai/model-configs?enabledOnly=false
POST   /api/v1/ai/model-configs
PUT    /api/v1/ai/model-configs/{id}
POST   /api/v1/ai/model-configs/{id}/enable
POST   /api/v1/ai/model-configs/{id}/disable
POST   /api/v1/ai/model-configs/{id}/priority
DELETE /api/v1/ai/model-configs/{id}
```

### 6.2 词汇学习缓存模块

主要文件：

- `controller/vocabulary/EnglishVocabularyStudyController.java`
- `service/vocabulary/EnglishVocabularyStudyService.java`
- `domain/entity/vocabulary/EnglishVocabularyStudyRecord.java`

接口：

```http
POST /api/v1/english/vocabularies/study
GET  /api/v1/english/vocabularies/{term}/best-match
GET  /api/v1/english/vocabularies/{term}
```

流程：

```text
输入 term
  -> normalizedTerm 归一化
  -> 若缓存存在且未 forceRefresh，返回缓存
  -> 若不存在或强制刷新，调用 Agent Chat
  -> 提取 JSON
  -> 保存 english_vocabulary_study_record
  -> 同步生成标签和关联词
  -> 返回学习卡片、标签、关联词
```

拼写容错：

- `/best-match` 在缓存词库中按编辑距离、前缀、包含关系计算匹配分。
- 命中后返回 `matchedTerm`、`normalizedTerm`、核心词性、核心含义、匹配分和完整学习卡片。
- 前端在学习请求失败时自动尝试展示最匹配单词，降低操作者输入错误带来的中断。

### 6.3 认证模块

主要文件：

- `controller/learning/AuthController.java`
- `service/learning/AuthService.java`
- `domain/entity/learning/LearningUser.java`
- `domain/entity/learning/LearningUserToken.java`

接口：

```http
POST /api/v1/learning/auth/register
POST /api/v1/learning/auth/login
POST /api/v1/learning/auth/logout
GET  /api/v1/learning/auth/me
```

说明：

- 当前使用轻量 Token 认证。
- 密码使用带盐 SHA-256 存储。
- Token 只在创建时返回明文，数据库保存 SHA-256 哈希。
- 注册或登录后自动确保默认词书存在。

### 6.4 词书与复习模块

主要文件：

- `controller/learning/WordbookController.java`
- `service/learning/WordbookService.java`
- `service/learning/VocabularyInsightService.java`

接口：

```http
GET  /api/v1/learning/wordbooks
POST /api/v1/learning/wordbooks
PUT  /api/v1/learning/wordbooks/{wordbookId}
GET  /api/v1/learning/wordbooks/{wordbookId}/entries
POST /api/v1/learning/wordbooks/{wordbookId}/entries
PUT  /api/v1/learning/wordbook-entries/{entryId}
DELETE /api/v1/learning/wordbook-entries/{entryId}
GET  /api/v1/learning/reviews/due
POST /api/v1/learning/reviews/{entryId}
```

词书词条状态：

- `familiar`：熟悉。
- `vague`：模糊。
- `forgotten`：遗忘。

词书词条笔记：

- 存储于 `learning_wordbook_entry.note`。
- 类型为 `TEXT`，前端按 Markdown 渲染。
- 学习页和复习页共用同一条词书词条数据，因此任一侧修改后两侧一致。

复习结果：

- `remembered`：阶段 +1，掌握度 +15。
- `vague`：阶段至少保持 1，掌握度 +5，次日复习。
- `forgotten`：阶段归 0，掌握度 -20，4 小时后复习。

复习间隔：

```text
0, 1, 2, 4, 7, 15, 30, 60 天
```

## 7. 数据库表

### 7.1 SQL 文件

已存在并可执行：

```text
src/main/resources/db/ai_agent_mysql.sql
src/main/resources/db/english_vocabulary_study_record_mysql.sql
src/main/resources/db/learning_user_wordbook_review_mysql.sql
src/main/resources/db/learning_vocabulary_relation_enrichment_mysql.sql
```

用户已说明 `ai_agent_mysql.sql` 已执行。新增表集中在：

```text
src/main/resources/db/learning_notes_model_wordbook_enhancement_mysql.sql
```

本次新增迁移包含：

- 创建 `ai_model_config`。
- 将 `learning_wordbook_entry.note` 调整为 Markdown 笔记 `TEXT`。
- 为 `learning_wordbook_entry` 增加 `status` 和索引。

如果已经执行过旧版 `learning_user_wordbook_review_mysql.sql`，且还没有执行过关系增强迁移，还需要额外执行：

```text
src/main/resources/db/learning_vocabulary_relation_enrichment_mysql.sql
```

该迁移会补充 `learning_vocabulary_relation` 的增强字段，并更新 `english_vocab_card_json` 模板，使后续 AI 输出包含搭配含义和相关词核心词性/含义。

### 7.2 AI Agent 表

| 表 | 说明 |
| --- | --- |
| `ai_agent` | Agent 配置 |
| `ai_prompt_template` | Prompt 模板 |
| `ai_chat_session` | Chat 会话 |
| `ai_chat_message` | Chat 消息 |
| `ai_model_call_record` | 模型调用记录 |
| `ai_model_config` | 可在个人信息页维护的模型配置 |

#### `ai_model_config`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `name` | 配置名称 |
| `provider` | 供应商编码，例如 `deepseek`、`kimi` |
| `model_name` | 模型名称 |
| `base_url` | Base URL |
| `chat_path` | Chat Completions 路径 |
| `api_key` | API Key |
| `enabled` | 是否启用 |
| `is_default` | 是否默认 |
| `sequence` | 优先级，数字越小越优先 |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

### 7.3 词汇缓存表

#### `english_vocabulary_study_record`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `term` | 用户输入词条 |
| `normalized_term` | 归一化词条，唯一 |
| `agent_code` | 使用的 Agent |
| `template_code` | 使用的模板 |
| `provider` | 模型供应商 |
| `model_name` | 模型名称 |
| `session_id` | AI 会话 ID |
| `raw_content` | AI 原始回复 |
| `parsed_json` | 解析后的 JSON |
| `token_usage` | Token 用量 |
| `cost_time` | 耗时毫秒 |
| `lookup_count` | 查询次数 |
| `last_lookup_time` | 最近查询时间 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

### 7.4 用户与词书表

#### `learning_user`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `username` | 登录用户名，唯一 |
| `nickname` | 昵称 |
| `password_hash` | 密码哈希 |
| `enabled` | 是否启用 |
| `last_login_time` | 最近登录时间 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### `learning_user_token`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `token_hash` | Token 哈希 |
| `expired_time` | 过期时间 |
| `revoked` | 是否撤销 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### `learning_wordbook`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `name` | 词书名称 |
| `description` | 词书描述 |
| `is_default` | 是否默认词书 |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### `learning_wordbook_entry`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `wordbook_id` | 词书 ID |
| `vocabulary_id` | 词汇缓存 ID |
| `term` | 展示词条 |
| `normalized_term` | 归一化词条 |
| `note` | Markdown 笔记 |
| `status` | 单词状态：`familiar`、`vague`、`forgotten` |
| `review_stage` | 复习阶段 |
| `mastery_score` | 掌握度 |
| `first_review_time` | 首次复习时间 |
| `last_review_time` | 最近复习时间 |
| `next_review_time` | 下次复习时间 |
| `due_count` | 进入复习队列次数 |
| `review_count` | 复习次数 |
| `correct_count` | 记住次数 |
| `wrong_count` | 忘记次数 |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

### 7.5 标签与关联表

#### `learning_vocabulary_tag`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `vocabulary_id` | 词汇缓存 ID |
| `normalized_term` | 归一化词条 |
| `tag_type` | 标签类型 |
| `tag_value` | 标签值 |
| `display_name` | 展示名称 |
| `weight` | 标签权重 |
| `source` | 来源 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

标签类型：

- `part_of_speech`
- `meaning_topic`
- `difficulty`
- `collocation`
- `word_family`

#### `learning_vocabulary_relation`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `vocabulary_id` | 当前词汇缓存 ID |
| `related_vocabulary_id` | 已入库关联词汇 ID，可空 |
| `normalized_term` | 当前词 |
| `related_term` | 关联词、短语或搭配 |
| `relation_type` | 关联类型 |
| `relation_value` | 关联说明或共享标签 |
| `related_part_of_speech` | 关联词核心词性 |
| `related_meaning` | 关联词或搭配核心含义 |
| `match_type` | 匹配来源：`parsed_object`、`parsed_text`、`cached_exact`、`fuzzy` |
| `match_score` | 匹配分数 |
| `score` | 相关度 |
| `source` | 来源 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

关联类型：

- `synonym`
- `antonym`
- `word_family`
- `collocation`
- `tag_overlap`

### 7.6 复习记录表

#### `learning_review_record`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `wordbook_id` | 词书 ID |
| `entry_id` | 词书词条 ID |
| `vocabulary_id` | 词汇缓存 ID |
| `normalized_term` | 归一化词条 |
| `result` | 复习结果 |
| `score` | 自评分 |
| `review_stage_before` | 复习前阶段 |
| `review_stage_after` | 复习后阶段 |
| `mastery_before` | 复习前掌握度 |
| `mastery_after` | 复习后掌握度 |
| `next_review_time` | 下次复习时间 |
| `duration_seconds` | 本次耗时 |
| `create_time` | 创建时间 |

## 8. 核心业务流程

### 8.1 学习流程

```text
登录
  -> 进入学习页
  -> 输入单词
  -> 查 english_vocabulary_study_record
  -> 命中缓存：直接返回
  -> 未命中：调用 Agent + Prompt + AI Provider
  -> 保存 raw_content 与 parsed_json
  -> 生成标签和关联词
  -> 前端展示学习卡片
  -> 用户加入当前词书
  -> 创建 learning_wordbook_entry
```

### 8.2 复习流程

```text
登录
  -> 选择词书
  -> 查询 /api/v1/learning/reviews/due
  -> 展示待复习词条
  -> 用户选择 忘记 / 模糊 / 记住
  -> 写入 learning_review_record
  -> 更新 learning_wordbook_entry 的 stage、mastery、next_review_time
```

### 8.3 标签与相关词流程

```text
词汇 parsed_json
  -> definitions 抽取词性
  -> definitions/memory_tips 推断含义主题
  -> term 长度和释义数量推断难度
  -> collocations / word_family 抽取标签
  -> synonyms / antonyms / word_family / collocations 生成关联词
  -> collocations 保存短语含义
  -> related 保存核心词性、核心含义、匹配来源和匹配分
  -> 已存在相同标签的词生成 tag_overlap 关系
```

## 8.4 前端产品结构

未登录时展示独立登录界面。登录后分为四个功能区：

- `个人信息`：账户概览、模型配置、词书管理、Agent 管理、系统日志和 Raw JSON。
- `单词本`：按词表查看单词、筛选状态、修改状态、删除词条、查看笔记。
- `学习`：查词、AI 学习卡片、音标发音、加入词书、例句、记忆提示、搭配、相关词、标签。
- `复习`：艾宾浩斯复习队列、复习卡片、复习结果提交。

学习页展示优先级：

```text
单词与音标
  -> 释义
  -> Examples
  -> Memory
  -> Collocations
  -> Related
  -> Tags
```

发音能力：

- 单词主按钮使用个人信息中配置的默认发音。
- UK / US 音标后分别提供发音按钮。
- 优先调用有道词典音频地址，失败后回退浏览器 SpeechSynthesis。

## 9. 安全与成本控制

- API Key 只通过环境变量或 `config/application-local.yaml` 注入。
- 模型调用记录不保存 Authorization。
- 词汇缓存按 `normalized_term` 唯一，避免重复调研 AI API。
- Token 在数据库中只保存哈希，退出登录会撤销 Token。
- 前端系统日志保存在浏览器本地，只用于用户排查最近操作。

## 10. 验证结果

已运行：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant
/Users/chandler/.m2/wrapper/dists/apache-maven-3.9.12-bin/5nmfsn99br87k5d4ajlekdq10k/apache-maven-3.9.12/bin/mvn test -DskipTests
```

结果：`BUILD SUCCESS`。

已运行：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
/usr/local/bin/node --check public/app.js
```

结果：通过，无语法错误。

前端服务：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
/usr/local/bin/node server.mjs
```

访问：

```text
http://127.0.0.1:5173
```

## 11. 后续迭代

- 将轻量 Token 认证升级为 Spring Security + JWT。
- 增加批量导入词表。
- 增加测验题和错题本。
- 增加复习算法配置，例如不同词书不同间隔策略。
- 增加真实服务端系统日志表，而不是只放前端 localStorage。
- 对数据库中的模型 API Key 增加加密存储或接入密钥管理服务。
- 引入 Flyway 或 Liquibase 管理 SQL 迁移。
