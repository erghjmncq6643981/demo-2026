# 英语学习助手产品说明

> 本文只描述当前已经实现的能力。后续方向见 `product-direction-and-goals.md`。

## 产品定位

系统面向自考英语、四级、六级、雅思及个人英语学习，将公共词表、个人计划、场景学习、知识反刍和复习组成学习闭环。

## 核心流程

1. 管理员导入、审核并发布公共词表。
2. 系统复用全局词汇语义资产，只分析尚未覆盖的单词。
3. 学习者选择公共词表和目标，创建个人学习计划（初识词汇）。
4. 系统按相关性生成场景材料，每篇最多包含 50 个核心词。
5. 学习者阅读材料，完成含义选择及必要的拼写检查，留存至个人单词本（记忆池）。
6. 进入知识反刍，在 2D 暗夜星系力导向图谱中激活 4D 衍生关联，编织微场景文章并完成微场景探针检索作答。
7. 系统保存逐词结果、点亮激活星系节点，并按艾宾浩斯曲线安排后续复习。

## 已实现功能

### 个人信息

- 管理账户、个人单词本、学习计划和学习设置。
- 展示学习统计与近一年活动热力图。

### 单词本

- 管理个人词条、掌握状态、词卡和 Markdown 笔记。
- 管理员导入自考、四级、六级、雅思等公共词表，支持 Markdown 表格、疑似断词筛选和人工修正。
- 公共词表语义分析按“语言 + 标准化单词”全局复用；部分成功立即保存，重试只处理未完成词。
- 词卡缓存全局复用；加入个人单词本后保留个人快照。

### 词汇大挑战

- 通过周/月日历查看每日场景摘要和待挑战词汇。
- 点击学习或回顾时再加载完整材料，支持提前生成后续场景。
- 场景材料包含文章、译文、核心词和相关词，笔记与材料关联。
- 每日词汇超过 50 个时均分为多篇材料，例如 80 个词拆为 2 篇各 40 词。
- 检查包含含义四选一；要求会拼写的单词增加跟敲和释义拼写。

### 知识反刍（原语境精读）

- **学习理念**：学习计划负责初识词汇，单词本充当留存与记忆池，知识反刍在深度主题场景中对词汇进行语义深加工与情境激活。
- **单一预置参考场景与默认 AI 模型动态宇宙**：
  - 预置保留 1 个标杆场景（`produce_kitchen` 生鲜采购与厨房料理），作为参考模板。
  - **自定义场景星系异步构建与两阶段恢复（Batch 2 已上线）**：用户点击「➕ 新场景」提交至 `POST /api/v1/learning/rumination/scenarios/custom/async`，提交期校验词本与输入快照并写入 `AiAsyncTask`（任务类型 `rumination_scenario`）后立即返回 `taskId`（同 `requestId` 或活跃快照幂等复用）。后台 `RuminationScenarioTaskHandler` 分两步执行：阶段 1（`generate_scenario`）在事务外调用默认 AI 模型（如 `deepseek-v4-flash`，不可用时平滑降级本地语义引擎，场景 `AiInvocationScene.RUMINATION_SCENARIO`）生成拓扑并写入 `checkpoint_json`；阶段 2（`persist_scenario`）在短事务内幂等落库至 `learning_rumination_graph`（阶段 2 失败重试直接复用检查点，不再重复调用模型）。构建期间 `GET /api/v1/learning/rumination/graph` 返回 `building=true` 的中心占位图并抑制空图覆盖保存，完成后前端自动加载持久化星系图谱。
  - **节点 4D 衍生裂变异步扩展与上下文隔离（Batch 2 已上线）**：选中焦点节点点击顶部「✨ 衍生裂变」调用 `POST /api/v1/learning/rumination/fission/async`，绑定原 `wordbookId`、`scenarioCode`、`parentNodeId`、`targetWord` 与现有词汇快照写入 `AiAsyncTask`（任务类型 `rumination_fission`）并立即返回 `taskId`。后台 `RuminationFissionTaskHandler` 分阶段执行（`generate_fission` 调用默认 AI 模型 `AiInvocationScene.RUMINATION_FISSION` 并持久化 `checkpoint_json` $\rightarrow$ `persist_fission` 通过 `FOR UPDATE` 图谱行锁在短事务内重新核验 `parentNodeId` 是否仍存在：若父节点已在等待期间被删除则安全跳过并返回 `skipped_parent_missing` 而不产生孤立节点或悬空边；存在则增量落库并通过 `GET /api/v1/learning/rumination/fission/tasks/{taskId}/result` 输出详情）。用户切换场景或刷新页面后，页面通过 `GET /api/v1/learning/rumination/tasks` 持续追踪同词本下全部活跃及近期反刍任务：切出原场景时完成通知仍照常展示，成果只归属原场景而绝不写入当前浏览图谱。
  - **场景材料与探针异步生成与两阶段恢复（Batch 1 已上线）**：点击「🚀 编织场景材料」调用 `POST /api/v1/learning/rumination/materials/generate/async`，提交期校验词本归属、场景归属与去重后至少 5 词的词袋，并将场景描述、词袋快照、词义快照与默认模型配置持久化写入 `AiAsyncTask`（任务类型 `rumination_material`）后立即返回 `taskId`，重复提交按 `requestId` 或活跃快照签名幂等复用已有任务。后台 `RuminationMaterialTaskHandler` 拆分为两个可恢复步骤：步骤 1（`generate_content`）在事务外调用默认 AI 模型（失败平滑降级本地引擎）并将产物与 `generation_source`（`ai` / `fallback`）写入步骤 `checkpoint_json`；步骤 2（`persist_material`）基于 `task_id` 唯一索引幂等落库 `learning_rumination_material` 与 `learning_rumination_probe`（未作答探针响应隐藏 `correctAnswer`），若阶段 2 失败重试，步骤 1 自动跳过，直接复用检查点落库而不再调用大模型。页面刷新或切换场景后通过 `GET /api/v1/learning/rumination/materials/task` 恢复进度横幅，材料卡片与阅读器明确标注来源（`🤖 AI 模型生成` / `⚙️ 本地语义降级`）。
- **2D 暗夜星系多环同心行星轨道布局与快速词卡**：
  - 基于 Canvas 物理引擎与多环同心行星轨道算法（$R_1=165/185$、$R_2=280/295$、$R_3=400$），广度优先（BFS）径向扇形展开 4D 衍生节点，新增/裂变节点自动避让已有节点以防重叠；左键聚焦节点独立渲染外圈高亮描边（与词袋成员和点亮状态区分），删除节点或切换场景时自动清理失效焦点；
  - 连线去除冗余中文标签干扰，通过色彩与线型直接表达认知维度（同类绿、动作蓝、加工橙、感官紫）；
  - **节点左键双击查卡**：在星系图中左键双击任意词汇节点，立即弹出快速词卡弹窗并优先读取全局词卡缓存（`GET /api/v1/english/vocabularies/{term}`）；若尚无缓存则自动调用默认 AI 模型实时生成并缓存词卡（`POST /api/v1/english/vocabularies/study`）。
  - **图谱变更异步同步入库**：节点左键长按拖拽移动、右击新增/修改/删除节点、AI 4D 衍生裂变及词袋切换等变更，均立即异步调用 `POST /api/v1/learning/rumination/graph/save` 更新 `learning_rumination_graph`，并与任务落库通过行级排他锁串行隔离，保障坐标、拓扑与词袋跨刷新一致恢复。
- **低干扰交互与非侵入式原料词袋（快照增量合并模型）**：
  - 顶部工具栏统一线性次序：`当前词本` $\rightarrow$ `主体场景` $\rightarrow$ `➕ 新场景`（弹窗创建）；
  - 保持 100% 全宽沉浸工作台，支持 `Cmd/Ctrl + 滚轮` 平滑缩放，与页面纵向平移滚动完全解耦；
  - 词袋交互无侧边抽屉打扰：选中节点后点击顶部【🎒 词袋】按键原地 Toggle（加入/移除）；
  - **提交快照增量扣减与等待期增词保留**：异步编织完成后仅从当前词袋 `bag_terms_json` 中扣除提交快照所包含的词汇，生成等待期间新加入词袋的词汇完整保留；任务失败或取消时不清空词袋；每篇生成的场景材料永久固化其专属 `target_words` 列表与 `generation_source`。
- **并列功能区与全链路持久化闭环**：
  - 下方提供「场景材料」与「微环境探针」并列导航入口，点击按需展开；
  - 探针未作答时服务端响应 `correctAnswer = null` 且前端不渲染正确答案属性；探针提交采用 `FOR UPDATE` 行级锁与 `WHERE answered_time IS NULL` 条件原子更新杜绝并发二次结算，仅首次作答更新掌握度、复习阶段并写入 `learning_review_record` 审计记录，衍生新词自动纳管至个人词本；
  - 建立 `learning_rumination_graph`（图谱拓扑与活跃词袋）、`learning_rumination_material`（场景材料文章、生成来源、`task_id` 幂等关联与固定原料词）、`learning_rumination_probe`（微环境探针与作答状态）三张独立数据库表，保障知识资产完整留存，下次进入无缝继续。

### 复习与词卡

- 按复习时间进入跟敲、例句回顾和记忆结果提交。
- 根据忘记、模糊、记住更新掌握度和下次复习时间。
- 支持快速查词、结构化词卡、音标、例句、搭配、相关词和音频播放。

### 系统管理

- 管理用户、公共词表、AI 任务、模型、Agent、Prompt、AI 会话和系统日志。
- AI 长任务持久化状态，提交后异步执行，支持进度、失败原因、取消和失败项重试。
- 模型配置包含供应商协议、请求适配、响应解析、上下文窗口和输出上限。

## 数据边界

- 公共词表、语义资产和词卡缓存可共享；个人词本、计划、笔记、进度、知识反刍探针与复习记录归当前用户所有。
- 场景列表、反刍主题和日历只返回摘要统计，图谱拓扑、文章、词卡、检测和笔记通过详情接口按需加载。
- 已生成材料作为学习历史保留；未掌握词可进入后续复习，但保留原场景归属。
- 知识反刍独立持久化：星系图谱拓扑与活跃原料词袋存储于 `learning_rumination_graph`，每篇生成的场景材料与固定原料词清单存储于 `learning_rumination_material`，关联微环境探针题库与作答记录存储于 `learning_rumination_probe`。探针作答不仅更新图谱内存状态，还原子落库更新词本词条掌握度、复习阶段，并写入 `learning_review_record` 审计记录；衍生新词自动纳管至个人词本 `learning_wordbook_entry`。
- 固定 AI 动作只发送当前任务所需数据，不携带长期学习历史。

## 运行形态

- 后端：Spring Boot、MyBatis-Plus、MySQL、JWT。
- 前端：原生 JavaScript 模块化应用。
- 无数据库时可使用前端 `?preview=1` 预览主要交互；真实数据与异步任务需要后端和 MySQL。
