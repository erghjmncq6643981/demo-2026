# 学习助手数据库脚本说明

## 新库初始化顺序

1. `init/00_ai_agent_init_mysql.sql`
2. `init/05_english_vocabulary_study_record_init_mysql.sql`
3. `init/10_learning_core_init_mysql.sql`
4. `91_learning_operational_patch_mysql.sql`
5. `92_learning_base_entity_audit_patch_mysql.sql`
6. `93_learning_article_study_mysql.sql`
7. `init/30_vocabulary_scene_plan_init_mysql.sql`

## 已有库补丁顺序

1. `90_learning_schema_patch_mysql.sql`
2. `91_learning_operational_patch_mysql.sql`
3. `92_learning_base_entity_audit_patch_mysql.sql`
4. `93_learning_article_study_mysql.sql`
5. `94_vocabulary_scene_plan_mysql.sql`
6. `95_ai_invocation_scene_mysql.sql`

## 说明

- `00` 负责 AI、会话、模型配置初始化。
- `05` 负责公共词汇缓存初始化。
- `10` 负责用户、单词本、复习、标签、关联词、偏好初始化。
- `90` 负责给旧会话表加 `user_id`、`scene_code` 等必要字段。
- `91` 负责系统日志、偏好表和运营增强表。
- `92` 负责给所有 DO 对应表补齐 `BaseEntity` 审计字段、逻辑删除和乐观锁版本号。
- `93` 负责文章学习记录表，以及英语文章学习 Agent / 模板初始化。
- `94` 负责词表导入审核、场景学习计划、逐词进度、批量词卡任务，以及场景规划 Agent / 模板初始化。
- `95` 负责给 AI 模型调用记录增加调用场景编码，便于按业务场景抽样和验收响应。
- 旧的分散 SQL 文件仍保留，便于比对和历史查看。
