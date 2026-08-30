# 学习助手数据库脚本

## 目录约定

- `schema/`：当前版本的完整建表脚本，只包含 `CREATE TABLE` 和索引定义。
- `init/00_ai_agent_seed_mysql.sql`：基础 Agent 和 Prompt 种子数据，可重复执行，不包含 API Key。
- 根目录的 `90-99_*.sql`：已有数据库的增量迁移历史，只用于升级旧库，不参与新库初始化。

## 自动迁移（推荐）

应用默认启用 Flyway：

- 空数据库执行 Java 基线迁移 `V1__BaselineSchema`，按下述顺序加载当前 schema 和种子数据。
- 未接入 Flyway 的存量非空数据库自动建立 `107` 基线，后续执行 `V108` 及更高版本迁移。
- 新增存量库变更必须创建 `V109__*` 及后续 Flyway 迁移，不再新增根目录手工脚本。
- 生产部署禁止 Flyway clean；部署前仍需备份数据库。

## 手工初始化

按以下顺序执行：

1. `schema/00_ai_schema_mysql.sql`
2. `schema/10_learning_core_schema_mysql.sql`
3. `schema/20_vocabulary_plan_schema_mysql.sql`
4. `schema/30_article_reading_schema_mysql.sql`
5. `schema/40_user_authorization_schema_mysql.sql`
6. `init/00_ai_agent_seed_mysql.sql`
7. `init/01_system_admin_seed_mysql.sql`

这样新库直接得到当前代码所需的最终结构，不需要再执行 `90-99` 补丁，也不会把建表和种子数据混在一起。

## 历史升级说明

107 及以前的脚本属于接入 Flyway 前的历史记录。已经升级到 107 的数据库直接由 Flyway 建立基线；更旧数据库需要先按其实际版本补齐历史变更，再启动新版应用。历史顺序如下：

1. `90_learning_schema_patch_mysql.sql`
2. `91_learning_operational_patch_mysql.sql`
3. `92_learning_base_entity_audit_patch_mysql.sql`
4. `93_learning_article_study_mysql.sql`
5. `94_vocabulary_scene_plan_mysql.sql`
6. `95_ai_invocation_scene_mysql.sql`
7. `96_learning_plan_date_and_status_mysql.sql`
8. `97_update_prompt_template_mysql.sql`
9. `98_vocabulary_scene_material_split_mysql.sql`
10. `99_article_guided_reading_mysql.sql`
11. `100_optimize_scene_prompt_template_mysql.sql`
12. `101_engineering_governance_mysql.sql`
13. `102_scene_material_note_mysql.sql`
14. `103_ai_async_task_mysql.sql`
15. `104_vocabulary_catalog_analysis_mysql.sql`
16. `105_learning_plan_generation_lock_mysql.sql`
17. `106_ai_model_catalog_agent_defaults_mysql.sql`
18. `107_agent_model_config_binding_mysql.sql`
19. Java 迁移 `V108__RecoverableAiTasksAndSceneMaterials`：可恢复任务步骤、执行尝试、材料版本和场景相关词
20. Java 迁移 `V109__SystemLogOutbox`：系统日志可靠异步投递 Outbox
21. Java 迁移 `V110__WordbookEntrySortIndex`：个人单词本词条列表复合排序索引，消除 Filesort
22. Java 迁移 `V111__DropAiForeignKeys`：移除 AI 模块物理外键约束，转为代码逻辑与事务保证数据完整性
23. Java 迁移 `V112__VocabularyMorphAlias`：创建英语词汇形态变形与别名索引表，支持名词复数与动词时态容错命中

迁移脚本都设计为可重复执行，但仍建议在执行前备份数据库并记录已执行版本。

## 当前领域结构

| 脚本 | 内容 |
| --- | --- |
| `00_ai_schema` | Agent、Prompt、会话、消息、模型调用记录、模型配置 |
| `10_learning_core_schema` | 用户、公共词卡缓存、个人单词本、复习记录、系统日志及日志 Outbox |
| `20_vocabulary_plan_schema` | Markdown/CSV 等词表导入、学习计划、场景单元、逐词进度、批量词卡任务 |
| `30_article_reading_schema` | 语境精读记录、阶段进度、阅读检测成绩 |

## 约束

- API Key 只允许保存后端加密后的密文，不在初始化 SQL 中提供真实密钥。
- 新增字段优先先更新 `schema/` 的当前完整结构，再为已执行旧库新增独立迁移文件。
- Flyway 接管后，每个存量变更必须同时更新当前完整 `schema/` 和新增的 `db/migration/V{版本}__*.sql`。
- 表和字段注释应说明业务含义，避免在业务代码中依赖未命名的魔法值。
