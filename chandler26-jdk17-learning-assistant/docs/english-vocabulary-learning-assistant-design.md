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
- 用户注册、登录、退出登录、Spring Security + JWT 鉴权。
- 词书创建、词书列表、词条加入词书、词条列表。
- 词书词条个人学习卡快照：学习页复用公共 AI 缓存，加入词书时复制个人详情，避免其他用户重新生成公共缓存后覆盖已有词条详情。
- 单词本独立菜单：按词表查看单词，按熟悉、模糊、遗忘筛选，支持修改状态和从词表删除。
- 学习/复习共享 Markdown 笔记：同一词书词条的笔记在学习页和复习页实时一致。
- 模型配置管理：个人信息页可新增、编辑、启用/禁用模型配置，并设置默认和优先级。
- 词汇标签：词性、含义主题、难度、搭配、词族。
- 词汇关联：同义词、反义词、词族、搭配、共享标签相近词。
- 艾宾浩斯复习计划：待复习队列、复习提交、下一次复习时间。
- 前端产品化页面：独立登录页；登录后个人信息、单词本、学习、复习四功能区。
- Raw JSON 移入个人信息页的 AI 会话区域。
- 服务端系统日志：注册/登录、模型配置、词书、词条、复习、AI 生成和缓存命中均持久化到 MySQL。
- 模型 API Key 加密存储：数据库保存 AES-GCM 密文，服务端调用模型前解密使用，前端只展示脱敏值。
- 复习页支持 qwerty-learner 风格跟敲：卡片置顶、按字母输入、错误抖动和提示音、完成后展示例句弹窗和喝彩效果。

暂未实现：

- 批量导入词表。
- 完整测验题生成与错题本。
- 更精细的间隔重复算法参数配置。
- Flyway/Liquibase 自动迁移。

## 3. 系统架构

```text
前端静态应用
    |
    | HTTP + Bearer JWT
    v
Spring Boot API
    |
    |-- AI Agent / Prompt / Chat / Model Config
    |-- Vocabulary Study Cache
    |-- Auth / Spring Security JWT
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
- 登录表单只保留账号、密码、登录、注册。
- 界面定位强调“把每一次查词变成可复习的知识资产”。

登录后：

- 左侧导航：个人信息、单词本、学习、复习。
- 顶部区域：当前功能标题、导航显示/隐藏、刷新 Agent。
- 个人信息：
  - 子导航：账户、单词本、Agent 管理、系统日志、AI 会话。
  - 账户概览：展示昵称、账号、词书数、单词数、待复习数；支持弹窗修改昵称和密码。
  - 学习活跃图：参考 GitHub contribution heatmap，根据学习量和复习量展示近一段时间活跃度。
  - 词书数量、单词数量、待复习数量。
  - 单词表管理：采用“列表 + 新增按钮 + 弹窗”的管理方式，支持新建/编辑词书、设置默认词书、单个删除词书；编辑使用图标按钮，不提供清空按钮。
  - Agent 管理：模型列表、学习 Agent、Prompt 模板、默认发音、强制刷新。
  - 模型列表：新增/编辑模型、配置 API Key 和 Base URL、启停、默认模型、优先级、单个删除；新增/编辑通过弹窗完成，供应商与模型明细联动，顺序使用数字输入，默认模型和启用状态使用按钮式开关单独成行，不提供清空按钮。
  - 学习 Agent 模板：可查看并修改完整模板信息、示例输入输出、模板内容和占位符；保存时校验声明的占位符必须存在。
  - 系统日志：AI 调用、缓存命中、模型配置、词书、复习提交、错误等，数据来自服务端日志表。
  - AI 会话：显示最近一次 AI 模型返回的原始数据。
- 单词本：
  - 选择当前词表后查看单词。
  - 支持按状态筛选：熟悉、模糊、遗忘。
  - “单词列表”只展示单词、熟练状态和掌握度，行高保持紧凑，标签和下次复习时间不在列表中展开。
  - 单词列表仅保留删除操作；熟练程度修改放在单词详情中通过弹窗选择。
  - 支持用红色删除图标从当前词表删除单词。
  - 右侧单词详情宽度约 80%，展示学习卡片内容：释义、例句、记忆提示、搭配、相关词和可展开标签。
  - 单词详情右上角展示下次复习时间，并支持“去复习”，直接跳转到复习界面并展示当前单词复习卡片。
  - 支持在单词详情中查看并编辑该词条的 Markdown 笔记。
- 学习：
  - 单词/短语输入。
  - 学习按钮后可选择 AI 模型配置。
  - 英语学习卡片：单词、音标、释义、例句、搭配、记忆提示。
  - 标签和相关单词。
  - 加入当前词书。
  - Markdown 学习笔记，和复习页笔记共用同一 `learning_wordbook_entry.note`。
  - 单词/例句发音。
- 复习：
  - 今日复习中选择词书和数量后直接进入复习卡片，不展示独立待复习任务列表。
  - 支持按单词字母跟敲，错误时跟敲框抖动并播放提示音。
  - 跟敲完成后弹窗展示例句，例句后提供发音按钮。
  - 完成时显示喝彩屏幕效果，再选择忘记、模糊或记住；弹窗中不提供单独的“下一个”按钮。
  - 选择“忘记了”后记录复习结果，并打开单词详情弹窗展示学习卡核心内容，帮助立即回看。
  - 选择“有点模糊”后记录复习结果并回到当前复习页。
  - 选择“记住了”后记录复习结果并自动切换到下一个单词。
  - 复习卡片支持上一个、下一个。
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

模型 API Key 安全：

- 数据库字段 `ai_model_config.api_key` 存储 AES-GCM 密文，格式为 `enc:v1:<iv>.<ciphertext>`。
- 加密密钥来自 `learning.security.api-key-secret`，建议生产环境使用 `LEARNING_API_KEY_SECRET` 注入。
- 旧版明文 API Key 读取时仍可兼容；新增或修改模型配置后会写入密文。
- 读取模型列表或调用模型时，如果发现旧版明文 API Key，服务端会透明改写为密文。
- 前端列表只展示 `apiKeyMasked`，不会回显明文。

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

Prompt 模板接口：

```http
GET /api/v1/ai/prompt-templates?type=user
GET /api/v1/ai/prompt-templates/code/{code}
PUT /api/v1/ai/prompt-templates/{id}
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

缓存边界：

- `english_vocabulary_study_record` 是公共 AI 学习卡缓存，同一归一化词条优先复用，减少重复调用模型。
- `forceRefresh=true` 会重新调用 AI 并更新公共缓存，供后续学习查询使用。
- 用户把词条加入词书时，系统会把当前公共缓存中的原始回复、解析 JSON、标签、关联词、模型供应商、模型名称和会话 ID 复制到 `learning_wordbook_entry` 的快照字段。
- 单词本详情、复习详情优先读取词书词条个人快照；历史词条若没有快照，才回退读取公共缓存。
- 因此其他用户重新生成同一个词条的公共 AI 结果，不会覆盖已经加入个人词书的学习卡详情。

拼写容错：

- `/best-match` 在缓存词库中按编辑距离、前缀、包含关系计算匹配分。
- 命中后返回 `matchedTerm`、`normalizedTerm`、核心词性、核心含义、匹配分和完整学习卡片。
- 前端在学习请求失败时自动尝试展示最匹配单词，降低操作者输入错误带来的中断。

### 6.3 认证模块

主要文件：

- `controller/learning/AuthController.java`
- `service/learning/AuthService.java`
- `domain/entity/learning/LearningUser.java`
- `config/SecurityConfig.java`
- `security/JwtAuthenticationFilter.java`
- `security/JwtTokenService.java`

接口：

```http
POST /api/v1/learning/auth/register
POST /api/v1/learning/auth/login
POST /api/v1/learning/auth/logout
GET  /api/v1/learning/auth/me
PUT  /api/v1/learning/auth/me
```

说明：

- 当前使用 Spring Security + JWT 认证，后端以无状态方式校验 `Authorization: Bearer <jwt>`。
- 密码使用带盐 SHA-256 存储。
- JWT 使用 HS256 签名，签名密钥来自 `learning.security.jwt-secret`，建议生产环境使用 `LEARNING_JWT_SECRET` 注入。
- `learning_user_token` 是旧版轻量 Token 表，JWT 升级后不再写入，可保留历史兼容或后续清理。
- 注册或登录后自动确保默认词书存在。
- 账户资料修改支持昵称修改；修改密码时需要提交当前密码和新密码。

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
DELETE /api/v1/learning/wordbooks/{wordbookId}
GET  /api/v1/learning/wordbooks/{wordbookId}/entries
POST /api/v1/learning/wordbooks/{wordbookId}/entries
PUT  /api/v1/learning/wordbook-entries/{entryId}
DELETE /api/v1/learning/wordbook-entries/{entryId}
GET  /api/v1/learning/activity?days=180
GET  /api/v1/learning/reviews/due
POST /api/v1/learning/reviews/{entryId}
```

系统日志接口：

```http
GET    /api/v1/learning/system-logs?limit=80
POST   /api/v1/learning/system-logs
DELETE /api/v1/learning/system-logs
```

说明：

- 系统日志持久化到 `learning_system_log`，不再只存浏览器 localStorage。
- 后端关键操作会写入日志：注册/登录、账户修改、词书/词条、复习提交、词汇缓存、AI 生成、模型配置变更。
- 前端仍会在操作发生时乐观展示日志，并异步写入服务端；刷新日志时以服务端数据为准。

词书删除：

- 采用软删除，删除词书时同步软删除该词书下的词条。
- 若删除默认词书，系统会将剩余最早创建的词书设为默认；若用户没有任何可用词书，后续访问词书列表会自动创建默认词书。

学习活跃图：

- `GET /api/v1/learning/activity` 聚合 `learning_wordbook_entry.create_time` 作为学习量，聚合 `learning_review_record.create_time` 作为复习量。
- 返回每日 `learnedCount`、`reviewCount`、`totalCount`，前端按 GitHub 贡献图样式渲染。

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

用户已说明 `ai_agent_mysql.sql` 已执行。新增迁移集中在：

```text
src/main/resources/db/learning_notes_model_wordbook_enhancement_mysql.sql
src/main/resources/db/security_jwt_system_log_api_key_encryption_mysql.sql
```

本次新增迁移包含：

- 创建 `ai_model_config`。
- 将 `learning_wordbook_entry.note` 调整为 Markdown 笔记 `TEXT`。
- 为 `learning_wordbook_entry` 增加 `status` 和索引。
- 创建 `learning_system_log`，用于服务端系统日志。
- 将 `ai_model_config.api_key` 调整为 `TEXT`，存储 AES-GCM 密文，兼容历史明文读取。

本次个人快照新增迁移：

```text
src/main/resources/db/learning_wordbook_entry_snapshot_mysql.sql
```

该迁移会为 `learning_wordbook_entry` 增加个人学习卡快照字段，并把已有词条按当前公共缓存回填一份快照。

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
| `learning_system_log` | 服务端系统日志 |

#### `ai_model_config`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `name` | 配置名称 |
| `provider` | 供应商编码，例如 `deepseek`、`kimi` |
| `model_name` | 模型名称 |
| `base_url` | Base URL |
| `chat_path` | Chat Completions 路径 |
| `api_key` | API Key AES-GCM 密文；兼容历史明文读取 |
| `enabled` | 是否启用 |
| `is_default` | 是否默认 |
| `sequence` | 优先级，数字越小越优先 |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### `learning_system_log`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `log_type` | 日志类型：`auth`、`ai`、`cache`、`review`、`wordbook`、`ai_model`、`error` 等 |
| `title` | 日志标题 |
| `detail` | 日志详情 |
| `source` | 来源：`server`、`client` |
| `business_type` | 关联业务类型 |
| `business_id` | 关联业务 ID |
| `create_time` | 创建时间 |

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
| `snapshot_raw_content` | 加入词书时的 AI 原始回复快照 |
| `snapshot_parsed_json` | 加入词书时解析出的 JSON 快照 |
| `snapshot_tags_json` | 加入词书时的标签快照 |
| `snapshot_relations_json` | 加入词书时的关联词快照 |
| `snapshot_provider` | 快照使用的模型供应商 |
| `snapshot_model_name` | 快照使用的模型名称 |
| `snapshot_session_id` | 快照关联的 AI 会话 ID |
| `snapshot_time` | 快照生成时间 |
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
  -> 复制当前公共学习卡为个人快照
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

未登录时展示独立登录界面。登录表单只保留账号、密码、登录和注册操作，后端地址等调试配置不暴露在登录主路径。

登录后分为四个功能区：

- `个人信息`：子导航包含账户、单词本、Agent 管理、系统日志、AI 会话；AI 会话展示模型返回 Raw JSON。
- `个人信息 / 账户`：支持编辑昵称和密码，并展示基于学习量、复习量生成的 GitHub 风格活跃图。
- `个人信息 / 单词本`：参考 Agent 管理中的模型管理方式，采用列表、刷新、新增弹窗、编辑弹窗、单个删除；不提供清空按钮。
- `个人信息 / Agent 管理`：模型配置采用列表和新增/编辑弹窗，列表操作只提供图标编辑和单个删除；默认和状态只在弹窗中修改，不提供清空按钮。
- `单词本`：按词表查看单词、筛选状态、在详情中通过弹窗修改状态、红色删除图标删除词条、查看学习卡片式详情、跳转当前单词复习卡片和编辑笔记。
- `学习`：查词、AI 学习卡片、音标发音、加入词书、例句、记忆提示、搭配、相关词、标签、继续追问。
- `复习`：在今日复习中选择词书和复习数量，直接进入复习卡片；卡片支持字母跟敲、错误抖动提示音、上一个/下一个切换、完成弹窗、例句发音和复习结果提交；忘记时展示学习卡核心详情，记住时自动进入下一个单词。

导航栏支持显示/隐藏：

- 桌面端隐藏后释放左侧宽度，内容区扩展。
- 移动端导航以抽屉方式覆盖显示，默认隐藏，不参与页面文档流高度，避免把功能页面挤到下方。
- 导航栏固定为视口高度，用户信息、后端连接状态和退出登录固定在左下角，不跟随右侧内容滚动。
- 右侧功能区使用独立滚动；单词本页中单词列表和单词详情分别滚动，学习页只滚动学习内容区域。
- 复习页“今日复习”头部固定在当前视口内容顶部；光标在复习卡或复习笔记中时，只滚动对应区域。

词书选择：

- 顶部全局区域不展示当前词书，避免不同功能页被同一选择状态隐式影响。
- `单词本` 页内部提供词表选择，用于查看该词表中的单词。
- `学习` 页点击加入词书时弹出词书列表，用户明确选择目标词书后再提交。
- 后端统一将 `Long` / Snowflake ID 序列化为字符串；前端所有词书、词条、模型配置 ID 都按字符串保存、比较和提交，避免浏览器 `JSON.parse` 丢失大整数精度。

单词本详情：

- 左侧列表标题为“单词列表”，只展示词条、熟练状态、掌握度和删除操作；行高保持紧凑，不随列表容器高度拉伸。
- 下次复习时间展示在单词详情右上角。
- 标签信息不在列表中铺开，详情中通过“查看标签”按钮展开。
- 熟练程度入口放在详情区域，通过弹窗选择 `熟悉`、`模糊`、`遗忘`。
- 删除词条按钮使用红色删除图标。
- 详情区域占主要宽度，展示学习卡片内容并复用学习/复习同一份 Markdown 笔记；“去复习”会直接切到当前单词的复习卡片。

学习页展示优先级：

```text
单词与音标
  -> 释义
  -> Examples
  -> Memory
  -> Collocations
  -> Related
  -> Tags
  -> 继续追问
```

发音能力：

- 单词主按钮使用个人信息中配置的默认发音。
- UK / US 音标后分别提供发音按钮。
- 复习完成弹窗中的例句播放使用个人信息中配置的默认发音。
- 优先调用有道词典音频地址，失败后回退浏览器 SpeechSynthesis。

## 9. 安全与成本控制

- 静态模型 API Key 只通过环境变量或 `config/application-local.yaml` 注入。
- 数据库模型配置的 `api_key` 使用 AES-GCM 加密存储，密钥来自 `learning.security.api-key-secret`。
- 模型调用记录不保存 Authorization。
- 词汇缓存按 `normalized_term` 唯一，避免重复调研 AI API。
- 用户认证使用 Spring Security + JWT，服务端无状态校验 `Authorization: Bearer <jwt>`。
- `learning_user_token` 为旧版轻量 Token 表，当前 JWT 认证不再写入。
- 系统日志持久化到 `learning_system_log`，前端仅保留乐观展示和短期页面状态。

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

- 增加批量导入词表。
- 增加测验题和错题本。
- 增加复习算法配置，例如不同词书不同间隔策略。
- 对数据库中的模型 API Key 接入更完整的密钥管理服务或定期轮换机制。
- 引入 Flyway 或 Liquibase 管理 SQL 迁移。
