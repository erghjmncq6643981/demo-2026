# 学习助手数据库脚本说明

## 新库初始化顺序

1. `init/00_ai_agent_init_mysql.sql`
2. `init/05_english_vocabulary_study_record_init_mysql.sql`
3. `init/10_learning_core_init_mysql.sql`

## 已有库补丁顺序

1. `90_learning_schema_patch_mysql.sql`
2. `91_learning_operational_patch_mysql.sql`

## 说明

- `00` 负责 AI、会话、模型配置初始化。
- `05` 负责公共词汇缓存初始化。
- `10` 负责用户、词书、复习、标签、关联词、偏好初始化。
- `90` 负责给旧会话表加 `user_id`、`scene_code` 等必要字段。
- `91` 负责系统日志、偏好表和运营增强表。
- 旧的分散 SQL 文件仍保留，便于比对和历史查看。
