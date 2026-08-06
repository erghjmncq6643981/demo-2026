# 英语词汇学习助手设计文档

## 1. 背景与目标

2022 年底，OpenAI 发布 ChatGPT 3.5，引发了全球对 AI 聊天机器人的广泛关注。随后，国内外厂商相继推出通义千问、豆包、DeepSeek、混元等大语言模型，AI 能力逐步从“尝鲜体验”走向“实际可用”。但通用型 AI 聊天机器人在垂直学习场景中仍然存在明显短板，例如生成内容不够精准、难以贴合个人学习进度、知识点难以沉淀与复习，无法形成稳定的学习闭环。

本人是一名具有十年工作经验的程序员，长期有英语学习需求，同时也持续关注 AI 技术在产品中的落地方式。基于此，自拟“英语学习助手”课题，借助多模型 AI 能力构建一款个性化学习工具，并以英语词汇学习作为切入点，后续可平滑扩展到编程学习、专业文献阅读、英文文章学习等更多场景。

本项目建设一个面向个人学习的英语词汇学习助手。第一阶段聚焦“输入英语单词或短语 -> 调用 AI 生成结构化学习卡片 -> 数据库缓存 -> 加入单词本 -> 按艾宾浩斯复习计划复习”的闭环，并扩展支持“从单词本选择词汇 -> 生成英语学习文章 -> 保存文章记录 -> 重复学习”的文章学习场景。

系统已接入 OpenAI-compatible 风格的大模型调用方式，可配置 Kimi、DeepSeek、豆包、元宝等供应商。当前实现优先支持 DeepSeek/Kimi 这类兼容 `/chat/completions` 的模型接口。

核心意义在于：

- 通过“AI 生成 -> 用户筛选 -> 笔记管理 -> 重复学习”的完整闭环，解决传统学习工具中“数据不精准、场景适配差、重点难提炼”三大痛点。
- 覆盖查词、学习、复习、文章阅读等核心环节，让单词、短语、例句、文章、语法知识点和笔记能够统一沉淀到数据库中。
- 借助单词本、标签、复习计划和学习文章的联动能力，提升用户的学习效率与知识留存率，为后续扩展到更多学习场景打下基础。

核心目标：

- 避免重复调用 AI：同一归一化词条优先读取数据库缓存。
- 将 AI 返回内容结构化：保存原始文本、解析 JSON、标签和关联词。
- 支持用户体系：登录后拥有自己的单词本和复习计划。
- 支持学习体验：前端拆分为登录、个人信息、单词本、学习、复习几个主区域。
- 支持后续扩展：保留 Agent、Prompt 模板、多供应商、系统日志和复习记录。

会话规则：

- AI 会话按“用户 + 学习场景”复用。
- 英语词汇学习只保留一个主会话，场景编码为 `english_vocabulary`。
- 文章、数学、拼音、写作等未来学习类型可使用不同场景编码，各场景内部仍只保留一个主会话。

## 2. 当前实现范围

已实现：

- AI Agent 配置、Prompt 模板、同步对话和模型调用记录。
- 英语词汇学习缓存接口。
- 用户注册、登录、退出登录、Spring Security + JWT 鉴权。
- 单词本创建、单词本列表、词条加入单词本、词条列表。
- 单词本词条个人学习卡快照：学习页复用公共 AI 缓存，加入单词本时复制个人详情，避免其他用户重新生成公共缓存后覆盖已有词条详情。
- 单词本独立菜单：按词表查看单词，按熟悉、模糊、遗忘筛选，支持修改状态和从词表删除。
- 单词本下的文章学习：支持选择单词本、按状态和前缀筛选词汇、勾选词汇小卡片，选择文章字数范围、难度和备注后调用 AI 生成英语学习文章；文章结果保存到数据库，可从历史记录重复打开学习。
- 学习/复习共享 Markdown 笔记：同一单词本词条的笔记在学习页和复习页实时一致。
- 模型配置管理：个人信息页可新增、编辑、启用/禁用模型配置，并设置默认和优先级。
- 用户偏好持久化：句子朗读的默认发音、音色、语速、音调保存到数据库，浏览器 localStorage 仅作为兜底。
- 词汇标签：词性、含义主题、难度、搭配、词族。
- 词汇关联：同义词、反义词、词族作为相关单词展示；搭配留在搭配区域，标签相近关系不进入相关单词展示。
- 艾宾浩斯复习计划：待复习队列、复习提交、下一次复习时间。
- 前端产品化页面：独立登录页；登录后个人信息、单词本、学习、复习四功能区。
- Raw JSON 移入个人信息页的 AI 会话区域。
- 服务端系统日志：注册/登录、模型配置、Agent、单词本、词条、复习、AI 生成和缓存命中均持久化到 MySQL。
- 模型 API Key 加密存储：数据库保存 AES-GCM 密文，服务端调用模型前解密使用，前端只展示脱敏值。
- 复习页支持 qwerty-learner 风格跟敲：卡片置顶、按字母输入、错误抖动和提示音、完成后展示例句弹窗和喝彩效果。
- 后端 DO 统一继承 `BaseEntity`，包含创建人、创建时间、更新人、更新时间、逻辑删除和乐观锁版本号。
- 有限取值通过枚举收敛：复习状态/结果、会话角色、学习场景、日志类型/来源、Agent 类型、Prompt 类型、词汇标签、词汇关系、匹配来源、发音口音等。

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
  - 账户概览：展示昵称、账号、单词本数、单词数、待复习数；支持弹窗修改昵称和密码。
  - 学习活跃图：参考 GitHub contribution heatmap，根据学习量和复习量展示近一段时间活跃度。
  - 单词本数量、单词数量、待复习数量。
  - 单词本管理：采用“列表 + 新增按钮 + 弹窗”的管理方式，支持新建/编辑单词本、设置默认单词本、单个删除单词本；编辑使用图标按钮，不提供清空按钮。
  - Agent 管理：模型列表、学习 Agent、Prompt 模板、默认发音、句子朗读偏好、强制刷新。
  - 模型列表：新增/编辑模型、配置 API Key 和 Base URL、启停、默认模型、优先级、单个删除；新增/编辑通过弹窗完成，供应商与模型明细联动，顺序使用数字输入，默认模型和启用状态使用按钮式开关单独成行，不提供清空按钮。
  - 学习 Agent 模板：可查看并修改完整模板信息、示例输入输出、模板内容和占位符；保存时校验声明的占位符必须存在。
  - 系统日志：AI 调用、缓存命中、模型配置、单词本、复习提交、错误等，数据来自服务端日志表。
  - AI 会话：显示最近一次 AI 模型返回的原始数据。
- 单词本：
  - 顶部 Tab：`单词列表` 和 `文章学习`。
  - 选择当前词表后查看单词。
  - 支持按状态筛选：熟悉、模糊、遗忘。
  - “单词列表”只展示单词、熟练状态和掌握度，行高保持紧凑，标签和下次复习时间不在列表中展开。
  - 单词列表仅保留删除操作；熟练程度修改放在单词详情中通过弹窗选择。
  - 支持用红色删除图标从当前词表删除单词。
  - 右侧单词详情宽度约 80%，展示学习卡片内容：释义、例句、记忆提示、搭配、相关词和可展开标签。
  - 单词详情右上角展示下次复习时间，并支持“去复习”，直接跳转到复习界面并展示当前单词复习卡片。
  - 支持在单词详情中查看并编辑该词条的 Markdown 笔记。
  - 文章学习：
    - 选择单词本后，可按熟练状态和前缀筛选词汇。
    - 筛选结果以多个小卡片展示，卡片底部有选择圆点，点击即可勾选或取消。
    - 支持文章字数范围枚举：`150-200`、`300-500`、`500-700`、`800-1000`。
    - 支持文章难度枚举：基础、适中、挑战。
    - 支持填写备注，例如指定商务语境、重点语法或文章语气。
    - 生成结果包含英文文章、中文译文、词汇用法、语法知识点、重点知识、练习题和学习建议。
    - 文章主体采用双语逐行对照展示：一行英文，一行对应中文。
    - 生成的文章会保存为历史记录，用户可以重复打开学习；相同用户、单词本、词汇组合、字数、难度和备注优先命中缓存，避免重复调用 AI。
- 学习：
  - 单词/短语输入。
  - 学习按钮后可选择 AI 模型配置。
  - 英语学习卡片：单词、音标、释义、例句、搭配、记忆提示。
  - 标签和相关单词。
  - 加入当前单词本。
  - Markdown 学习笔记，和复习页笔记共用同一 `learning_wordbook_entry.note`。
  - 单词/例句发音，其中句子朗读偏好会自动保存到用户偏好表。
- 复习：
  - 今日复习中选择单词本和数量后直接进入复习卡片，不展示独立待复习任务列表。
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
| `public/app.js` | 前端入口，负责加载模块化应用 |
| `public/src/app/**` | 应用壳、全局状态、接口服务、事件绑定和预览模式 |
| `public/src/features/**` | 个人信息、单词本、学习、复习、发音等业务功能模块 |
| `public/src/shared/**` | 弹窗、ID 归一、文本、存储、词汇和单词本等共享工具 |
| `public/styles.css` | 产品化样式，参考 qwerty-learner 的学习体验 |
| `server.mjs` | 零依赖静态文件服务 |

启动：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

设计预览模式：

```text
http://127.0.0.1:5173/?preview=1
```

说明：`preview=1` 只用于无后端或未登录时查看产品化界面，会在浏览器内注入模拟用户、单词本、复习任务和词汇学习卡片，不影响真实登录和接口调用流程。

## 5. AI Agent 设计

### 5.1 模型供应商配置

配置来源：

```text
MySQL: ai_model_config
```

模型供应商、明细模型、Base URL、Chat Path、API Key、启停状态、默认标记和优先级只允许保存在数据库 `ai_model_config`。后端 `application.yaml` 不再提供 `learning.ai.providers` 静态配置，也不再通过环境变量注入具体模型 API Key。

`application.yaml` 只保留安全类配置，例如 JWT 密钥和 `learning.security.api-key-secret`。其中 `api-key-secret` 是数据库 API Key 密文的加密密钥，不是模型连接信息。

运行时模型配置优先级：

1. 学习页明确选择的 `modelConfigId`。
2. 数据库 `ai_model_config` 中启用且标记默认的配置。
3. 数据库 `ai_model_config` 中启用且优先级最高的配置。

如果数据库中没有可用模型配置，后端会返回业务可读错误：提示操作者到 `个人信息 - Agent管理 - 模型管理` 新增并启用模型。

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

前端模型管理弹窗中的供应商和模型明细候选项由接口返回的数据库模型列表推导；新增第一条模型时需要手工输入供应商、模型明细、Base URL、Chat Path 和 API Key。

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
- `examples` 每条包含 `sentence`、`translation`，其中 `sentence` 是英文例句，`translation` 是对应中文翻译。
- `collocations` 使用对象数组，每条包含 `phrase`、`meaning`，用于展示搭配含义。
- `synonyms`、`antonyms`、`word_family` 使用对象数组，每条包含 `word`、`part_of_speech`、`meaning`、`phonetic.uk`、`phonetic.us`，用于展示相关词核心词性、核心含义和英音/美音音标。
- 如果用户输入疑似拼写错误，AI 可在 `term` 中输出判断后的标准单词；系统也提供缓存层面的最匹配词查询。

文章学习模板要求 AI 返回 JSON，核心字段包括：

- `title`
- `level`
- `word_count_range`
- `difficulty`
- `article`
- `translation`
- `vocabulary_focus`
- `grammar_points`
- `key_points`
- `practice`
- `study_tips`

结构约束：

- `article` 是自然连贯的英文文章，尽量覆盖用户勾选的所有词汇。
- `translation` 是整篇文章的中文译文。
- `vocabulary_focus` 每条包含 `word`、`meaning`、`usage`、`sentence`、`translation`。
- `grammar_points` 每条包含 `title`、`explanation`、`examples`，其中 examples 包含英文例句和中文翻译。
- `key_points` 和 `study_tips` 使用中文，便于学习者快速复盘。
- `practice` 每题包含 `question`、`answer`、`explanation`。

前端和后端都兼容常见字段变体，例如：

- `meaning` / `meaning_cn`
- `english` / `meaning_en`
- `translation` / `translation_cn`
- `part_of_speech` / `pos`
- 字符串数组或对象数组形式的 `collocations`
- 字符串数组或对象数组形式的 `synonyms`、`antonyms`、`word_family`
- 例句中文翻译字段兼容 `translation_cn`、`translation`、`zh`、`chinese_translation`、`sentence_cn`、`example_translation` 等常见变体。

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
- 用户把词条加入单词本时，系统会把当前公共缓存中的原始回复、解析 JSON、标签、关联词、模型供应商、模型名称和会话 ID 复制到 `learning_wordbook_entry` 的快照字段。
- 如果词条已经存在于该单词本，用户再次从学习页保存到单词本时，系统会比较公共缓存与个人快照；当公共缓存来自更晚的 AI 生成结果时，刷新该词条的个人学习卡快照，但保留笔记、熟练度、复习阶段和下次复习时间。
- 单词本详情、复习详情优先读取单词本词条个人快照；历史词条若没有快照，才回退读取公共缓存。
- 因此其他用户重新生成同一个词条的公共 AI 结果，不会覆盖已经加入个人单词本的学习卡详情。

拼写容错：

- `/best-match` 在缓存词库中按编辑距离、前缀、包含关系计算匹配分。
- 命中后返回 `matchedTerm`、`normalizedTerm`、核心词性、核心含义、匹配分和完整学习卡片。
- 前端在学习请求失败时自动尝试展示最匹配单词，降低操作者输入错误带来的中断。

### 6.3 文章学习模块

主要文件：

- `controller/learning/ArticleStudyController.java`
- `service/learning/ArticleStudyService.java`
- `domain/entity/learning/LearningArticleStudyRecord.java`

接口：

```http
POST /api/v1/learning/articles/study
GET  /api/v1/learning/articles?wordbookId={id}&limit=10
GET  /api/v1/learning/articles/{recordId}
```

流程：

```text
选择单词本词条
  -> 校验词条归属当前用户和单词本
  -> 抽取每个词条的核心词性和核心含义
  -> 按用户、单词本、词汇组合、字数、难度、备注生成缓存哈希
  -> 若缓存存在且未 forceRefresh，返回文章学习记录
  -> 若缓存不存在或强制刷新，调用 english_article 场景 Agent
  -> 提取 JSON
  -> 保存 learning_article_study_record
  -> 返回文章、译文、词汇用法、语法点、重点知识、练习题和学习建议
```

缓存和复学边界：

- `learning_article_study_record` 是用户级文章学习记录，不是公共词汇缓存。
- AI 会话使用 `english_article` 学习场景，同一用户在该场景复用一个 `ai_chat_session`。
- 相同用户、单词本、词汇组合、字数范围、难度和备注优先命中缓存。
- `forceRefresh=true` 会生成一条新的文章学习记录，历史记录仍可重复打开学习。
- 字数范围和难度都通过枚举约束，避免前端传入任意值导致提示词不可控。

### 6.4 认证模块

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
- 注册或登录后自动确保默认单词本存在。
- 账户资料修改支持昵称修改；修改密码时需要提交当前密码和新密码。

### 6.5 单词本与复习模块

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
GET  /api/v1/learning/reviews/restart?wordbookId={id}&limit=10
POST /api/v1/learning/reviews/{entryId}
```

系统日志接口：

```http
GET    /api/v1/learning/system-logs?limit=80
POST   /api/v1/learning/system-logs
DELETE /api/v1/learning/system-logs
```

用户偏好接口：

```http
GET  /api/v1/learning/preferences/speech
PUT  /api/v1/learning/preferences/speech
```

说明：

- 系统日志持久化到 `learning_system_log`，不再只存浏览器 localStorage。
- 后端关键操作会写入日志：注册/登录、账户修改、单词本/词条、复习提交、词汇缓存、AI 生成、模型配置、Agent 变更。
- 前端仍会在操作发生时乐观展示日志，并异步写入服务端；刷新日志时以服务端数据为准。
- 业务日志使用 info 级别，文案面向业务人员，例如“用户「小明」把单词「abandon」添加到单词本「默认单词本」”。
- 运行诊断日志使用 debug 级别，面向技术排查。

单词本删除：

- 采用软删除，删除单词本时同步软删除该单词本下的词条。
- 若删除默认单词本，系统会将剩余最早创建的单词本设为默认；若用户没有任何可用单词本，后续访问单词本列表会自动创建默认单词本。

学习活跃图：

- `GET /api/v1/learning/activity` 聚合 `learning_wordbook_entry.create_time` 作为学习量，聚合 `learning_review_record.create_time` 作为复习量。
- 返回每日 `learnedCount`、`reviewCount`、`totalCount`，前端按 GitHub 贡献图样式渲染。

单词本词条状态：

- `familiar`：熟悉。
- `vague`：模糊。
- `forgotten`：遗忘。

单词本词条笔记：

- 存储于 `learning_wordbook_entry.note`。
- 类型为 `TEXT`，前端按 Markdown 渲染。
- 学习页和复习页共用同一条单词本词条数据，因此任一侧修改后两侧一致。

复习结果：

- `remembered`：阶段 +1，掌握度 +15。
- `vague`：阶段至少保持 1，掌握度 +5，次日复习。
- `forgotten`：阶段归 0，掌握度 -20，4 个清醒小时后复习。

睡眠时间保护：

- `00:00` 到 `06:00` 视为睡眠时间，不生成复习任务。
- 如果按间隔计算出的复习时间落入睡眠时间，自动顺延到当天 `06:00`。
- 如果用户在睡眠时间提交复习，本次排期从当天 `06:00` 作为起算点，避免“4 小时后”落在凌晨。

复习间隔：

```text
0, 1, 2, 4, 7, 15, 30, 60 天
```

### 6.6 后端企业化约束

基础 DO：

- 所有持久化 DO 继承 `BaseEntity`。
- 统一字段：`create_by`、`create_time`、`update_by`、`update_time`、`deleted`、`version`。
- `LearningAuditor` 从 Spring Security 上下文读取当前用户 ID，匿名或系统上下文回退为 `0`。
- `MybatisPlusConfig` 统一填充审计字段，并启用 MyBatis-Plus 乐观锁拦截器。

枚举化：

- 有限值统一放入 `domain/enums`，数据库仍保存稳定 code。
- 已枚举：`AiAgentType`、`PromptTemplateType`、`ChatMessageRole`、`LearningScene`、`ReviewStatus`、`ReviewResult`、`ArticleDifficulty`、`ArticleWordCountRange`、`SpeechVoiceType`、`SystemLogType`、`SystemLogSource`、`VocabularyTagType`、`VocabularyRelationType`、`VocabularyDifficulty`、`VocabularyMatchType`。

充血对象：

- `LearningWordbookEntry` 封装创建、复制、移动、恢复、软删除、快照刷新、复习计数和状态流转。
- `LearningWordbook` 封装默认单词本创建、资料更新、默认状态切换和软删除。
- 服务层负责事务、Mapper、远程调用和日志编排，不直接散落状态字段组合赋值。

异常：

- API 业务异常使用 `LearningAssistantException`，携带错误码和业务可读消息。
- 技术排查类异常保留 cause，避免吞掉原始错误。

## 7. 数据库表

### 7.1 SQL 文件

新库初始化建议顺序：

```text
src/main/resources/db/init/00_ai_agent_init_mysql.sql
src/main/resources/db/init/05_english_vocabulary_study_record_init_mysql.sql
src/main/resources/db/init/10_learning_core_init_mysql.sql
src/main/resources/db/91_learning_operational_patch_mysql.sql
src/main/resources/db/92_learning_base_entity_audit_patch_mysql.sql
src/main/resources/db/93_learning_article_study_mysql.sql
```

已有库补丁建议顺序：

```text
src/main/resources/db/90_learning_schema_patch_mysql.sql
src/main/resources/db/91_learning_operational_patch_mysql.sql
src/main/resources/db/92_learning_base_entity_audit_patch_mysql.sql
src/main/resources/db/93_learning_article_study_mysql.sql
```

脚本职责：

- `00_ai_agent_init_mysql.sql`：AI Agent、Prompt 模板、AI 会话、消息、调用记录、模型配置。
- `05_english_vocabulary_study_record_init_mysql.sql`：公共英语词汇学习缓存。
- `10_learning_core_init_mysql.sql`：用户、单词本、词条快照、标签、关联词、复习记录、用户偏好。
- `90_learning_schema_patch_mysql.sql`：旧会话表补 `user_id`、`scene_code`。
- `91_learning_operational_patch_mysql.sql`：系统日志、模型配置、用户偏好等运营增强表，使用 `CREATE TABLE IF NOT EXISTS`，新库也可执行。
- `92_learning_base_entity_audit_patch_mysql.sql`：给所有 DO 对应表补齐 `BaseEntity` 审计字段、逻辑删除、乐观锁版本号和必要索引。
- `93_learning_article_study_mysql.sql`：文章学习记录表，以及英语文章学习 Agent / Prompt 模板初始化。

说明：

- 旧的零散 SQL 文件仍保留作为历史比对，不再作为主执行入口。
- 已有库如果已经执行到 `92`，本次只需要继续执行 `93_learning_article_study_mysql.sql`。
- `92` 是幂等脚本，可重复执行；如果表或字段已经存在会自动跳过。
- 模型 API Key 仍只保存 AES-GCM 密文，禁止在 SQL、源码或文档中写入真实密钥。

公共审计字段：

| 字段 | 说明 |
| --- | --- |
| `create_by` | 创建人用户 ID；系统上下文为 `0` |
| `create_time` | 创建时间 |
| `update_by` | 更新人用户 ID；系统上下文为 `0` |
| `update_time` | 更新时间 |
| `deleted` | 逻辑删除标记 |
| `version` | 乐观锁版本号 |

下列表结构默认省略公共审计字段；实际建表以初始化 SQL 和 `92` 补丁后的结果为准。

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

#### `ai_chat_session`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `agent_code` | Agent 编码 |
| `business_type` | 业务类型 |
| `business_id` | 业务 ID |
| `scene_code` | 学习场景编码 |
| `title` | 会话标题 |
| `variables_json` | 会话级变量 JSON |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

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
| `log_type` | 日志类型：`auth`、`ai`、`cache`、`review`、`wordbook`、`ai_model`、`agent`、`preference`、`error` 等 |
| `title` | 日志标题 |
| `detail` | 日志详情 |
| `source` | 来源：`server`、`client` |
| `business_type` | 关联业务类型 |
| `business_id` | 关联业务 ID |
| `create_time` | 创建时间 |

#### `learning_user_preference`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `preference_key` | 偏好键，例如 `speech.voice_type` |
| `preference_value` | 偏好值 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

偏好键：

- `speech.voice_type`
- `speech.sentence_voice_name`
- `speech.sentence_rate`
- `speech.sentence_pitch`

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

#### `learning_article_study_record`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `wordbook_id` | 单词本 ID |
| `selected_terms_json` | 生成文章时选择的词汇摘要 JSON |
| `selected_term_hash` | 用户、单词本、词汇组合、字数、难度和备注生成的缓存哈希 |
| `word_count_range` | 文章字数范围：`150-200`、`300-500`、`500-700`、`800-1000` |
| `difficulty` | 文章难度：`easy`、`medium`、`hard` |
| `remark` | 用户输入的文章生成备注或学习要求 |
| `agent_code` | 调用的 Agent 编码 |
| `template_code` | 调用的提示词模板编码 |
| `provider` | 模型供应商 |
| `model_name` | 模型名称 |
| `session_id` | AI 会话 ID |
| `raw_content` | AI 原始回复 |
| `parsed_json` | 解析后的文章学习 JSON |
| `token_usage` | Token 用量 |
| `cost_time` | 耗时毫秒 |
| `lookup_count` | 读取次数 |
| `last_lookup_time` | 最近读取时间 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

### 7.4 用户与单词本表

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
| `name` | 单词本名称 |
| `description` | 单词本描述 |
| `is_default` | 是否默认单词本 |
| `deleted` | 是否删除 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### `learning_wordbook_entry`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `wordbook_id` | 单词本 ID |
| `vocabulary_id` | 词汇缓存 ID |
| `term` | 展示词条 |
| `normalized_term` | 归一化词条 |
| `note` | Markdown 笔记 |
| `snapshot_raw_content` | 加入单词本时的 AI 原始回复快照 |
| `snapshot_parsed_json` | 加入单词本时解析出的 JSON 快照 |
| `snapshot_tags_json` | 加入单词本时的标签快照 |
| `snapshot_relations_json` | 加入单词本时的关联词快照 |
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
| `related_term` | 关联词 |
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

当前展示的相关单词类型：

- `synonym`
- `antonym`
- `word_family`

保留类型：

- `collocation`：搭配只展示在 Collocations 区域，不进入 Related。
- `tag_overlap`：标签相似关系预留，不进入当前 Related 展示。

### 7.6 复习记录表

#### `learning_review_record`

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `wordbook_id` | 单词本 ID |
| `entry_id` | 单词本词条 ID |
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
  -> 用户加入当前单词本
  -> 若单词本中不存在该词条，创建 learning_wordbook_entry
  -> 若单词本中已存在该词条，保留学习状态并刷新个人学习卡快照
  -> 复制当前公共学习卡为个人快照
```

### 8.2 复习流程

```text
登录
  -> 选择单词本
  -> 查询 /api/v1/learning/reviews/due
  -> 展示待复习词条
  -> 如果没有到期词条，用户点击“开始复习”时弹窗二次确认
  -> 用户确认后调用 /api/v1/learning/reviews/restart 重新生成本轮复习任务
  -> 重新生成任务只返回本轮队列，不立即修改 next_review_time
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
  -> synonyms / antonyms / word_family 生成相关单词
  -> collocations 作为搭配标签和学习卡搭配区域展示
  -> related 保存核心词性、核心含义、匹配来源和匹配分
```

## 8.4 前端产品结构

未登录时展示独立登录界面。登录表单只保留账号、密码、登录和注册操作，后端地址等调试配置不暴露在登录主路径。

登录后分为四个功能区：

- `个人信息`：子导航包含账户、单词本、Agent 管理、系统日志、AI 会话；AI 会话展示模型返回 Raw JSON。
- `个人信息 / 账户`：支持编辑昵称和密码，并展示基于学习量、复习量生成的 GitHub 风格活跃图。
- `个人信息 / 单词本`：参考 Agent 管理中的模型管理方式，采用列表、刷新、新增弹窗、编辑弹窗、单个删除；不提供清空按钮。
- `个人信息 / Agent 管理`：模型配置采用列表和新增/编辑弹窗，列表操作只提供图标编辑和单个删除；默认和状态只在弹窗中修改，不提供清空按钮。
- `单词本`：按词表查看单词、筛选状态、在详情中通过弹窗修改状态、红色删除图标删除词条、查看学习卡片式详情、跳转当前单词复习卡片和编辑笔记。
- `学习`：查词、AI 学习卡片、音标发音、加入单词本、例句、记忆提示、搭配、相关词、标签、继续追问。
- `复习`：在今日复习中选择单词本和复习数量，直接进入复习卡片；如果已经完成今日任务，再次点击开始复习会二次确认并重新生成一组本轮复习任务；卡片支持字母跟敲、错误抖动提示音、上一个/下一个切换、完成弹窗、例句发音和复习结果提交；忘记时展示学习卡核心详情，记住时自动进入下一个单词。

导航栏支持显示/隐藏：

- 桌面端隐藏后释放左侧宽度，内容区扩展。
- 移动端导航以抽屉方式覆盖显示，默认隐藏，不参与页面文档流高度，避免把功能页面挤到下方。
- 导航栏固定为视口高度，用户信息、后端连接状态和退出登录固定在左下角，不跟随右侧内容滚动。
- 右侧功能区使用独立滚动；单词本页中单词列表和单词详情分别滚动，学习页只滚动学习内容区域。
- 复习页“今日复习”头部固定在当前视口内容顶部；光标在复习卡或复习笔记中时，只滚动对应区域。

单词本选择：

- 顶部全局区域不展示当前单词本，避免不同功能页被同一选择状态隐式影响。
- `单词本` 页内部提供词表选择，用于查看该词表中的单词。
- `学习` 页点击加入单词本时弹出单词本列表，用户明确选择目标单词本后再提交。
- 后端统一将 `Long` / Snowflake ID 序列化为字符串；前端所有单词本、词条、模型配置 ID 都按字符串保存、比较和提交，避免浏览器 `JSON.parse` 丢失大整数精度。

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
- 搭配词组提供发音按钮；点击词组去学习前需要二次确认。
- 相关单词展示核心词性、核心含义、可推导音标和发音按钮；点击相关单词去学习前需要二次确认。
  - 复习完成弹窗中的例句播放使用个人信息中配置的默认发音和句子朗读偏好。
- 优先调用有道词典音频地址，失败后回退浏览器 SpeechSynthesis。

## 9. 安全与成本控制

- 模型连接信息只保存在数据库 `ai_model_config`，不在 `application.yaml`、前端静态配置或文档中维护第二份连接配置。
- 数据库模型配置的 `api_key` 使用 AES-GCM 加密存储，密钥来自 `learning.security.api-key-secret`。
- 模型调用记录不保存 Authorization。
- 词汇缓存按 `normalized_term` 唯一，避免重复调研 AI API。
- 用户认证使用 Spring Security + JWT，服务端无状态校验 `Authorization: Bearer <jwt>`。
- `learning_user_token` 为旧版轻量 Token 表，当前 JWT 认证不再写入。
- 系统日志持久化到 `learning_system_log`，前端仅保留乐观展示和短期页面状态。
- 句子朗读设置持久化到 `learning_user_preference`，前端 localStorage 仅作为登录失败或加载失败时的兜底缓存。

## 10. 验证结果

已运行：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant
mvn -q -DskipTests compile
```

结果：通过。

已运行：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant
mvn -q -Dtest=WordbookServiceScheduleTest test
```

结果：通过。

历史前端语法检查：

```bash
cd /Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant-web
node --check public/app.js
```

结果：通过。

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
- 增加复习算法配置，例如不同单词本不同间隔策略。
- 对数据库中的模型 API Key 接入更完整的密钥管理服务或定期轮换机制。
- 引入 Flyway 或 Liquibase 管理 SQL 迁移。
