# 学习助手数据库脚本

## 目录约定

- `schema/`：当前版本的完整建表脚本，只包含 `CREATE TABLE` 和索引定义。
- `init/00_ai_agent_seed_mysql.sql`：基础 Agent 和 Prompt 种子数据，可重复执行，不包含 API Key。
- 根目录的 `90-99_*.sql`：已有数据库的增量迁移历史，只用于升级旧库，不参与新库初始化。

## 新库初始化

按以下顺序执行：

1. `schema/00_ai_schema_mysql.sql`
2. `schema/10_learning_core_schema_mysql.sql`
3. `schema/20_vocabulary_plan_schema_mysql.sql`
4. `schema/30_article_reading_schema_mysql.sql`
5. `init/00_ai_agent_seed_mysql.sql`

这样新库直接得到当前代码所需的最终结构，不需要再执行 `90-99` 补丁，也不会把建表和种子数据混在一起。

## 已有库升级

已有数据库不要重新执行 `schema/`，按实际版本继续执行尚未执行的迁移。完整顺序如下：

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

迁移脚本都设计为可重复执行，但仍建议在执行前备份数据库并记录已执行版本。

## 当前领域结构

| 脚本 | 内容 |
| --- | --- |
| `00_ai_schema` | Agent、Prompt、会话、消息、模型调用记录、模型配置 |
| `10_learning_core_schema` | 用户、公共词卡缓存、个人单词本、复习记录、系统日志 |
| `20_vocabulary_plan_schema` | Markdown/CSV 等词表导入、学习计划、场景单元、逐词进度、批量词卡任务 |
| `30_article_reading_schema` | 语境精读记录、阶段进度、阅读检测成绩 |

## 约束

- API Key 只允许保存后端加密后的密文，不在初始化 SQL 中提供真实密钥。
- 新增字段优先先更新 `schema/` 的当前完整结构，再为已执行旧库新增独立迁移文件。
- 表和字段注释应说明业务含义，避免在业务代码中依赖未命名的魔法值。
