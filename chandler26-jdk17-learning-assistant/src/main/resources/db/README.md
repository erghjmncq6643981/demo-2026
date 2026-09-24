# 数据库初始化与迁移

## 新数据库

按顺序执行：

1. `schema/00_ai_schema_mysql.sql`
2. `schema/10_learning_core_schema_mysql.sql`
3. `schema/20_vocabulary_plan_schema_mysql.sql`
4. `schema/30_article_reading_schema_mysql.sql`
5. `schema/40_user_authorization_schema_mysql.sql`
6. `init/00_ai_agent_seed_mysql.sql`
7. `init/01_system_admin_seed_mysql.sql`

`schema` 是当前完整结构，`init` 只保存可重复执行的基础数据，不包含真实 API Key。

## 已有数据库

Flyway 默认关闭；启用需设置 `LEARNING_FLYWAY_ENABLED=true`。应用配置以版本 107 为非空旧库基线，当前源码包含：

- V113：性能查询索引。
- V114：活动统计与 AI 会话索引。
- V115：异步任务日历索引。
- V116：学习活动事件和日汇总。
- V117：全局词汇语义资产。

升级前必须备份数据库并检查 `flyway_schema_history`。如果旧库尚未达到 112，不得直接执行当前迁移，应先使用对应历史版本代码或备份脚本补齐结构。

## 维护规则

- 新增结构先更新当前 `schema`，再新增下一编号迁移；禁止改写已执行迁移。
- 迁移与种子数据必须可重复执行，不依赖本机账号或真实凭据。
- 表、字段和索引需说明业务含义；批量数据通过批量 SQL 或分块处理。
- 生产环境禁止 Flyway clean。
- 数据库不可用时只能报告未验证，不能把 Mock 测试视为迁移成功。

## 验证

- Mapper XML 使用项目校验测试解析。
- 数据库工作流优先在隔离库或随机测试表验证。
- 全局词汇语义资产测试可设置 `LEARNING_SEMANTIC_MYSQL_TEST=true` 后运行对应测试。
