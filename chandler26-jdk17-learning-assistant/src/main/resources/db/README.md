# 数据库初始化说明

本目录保存 English Learning Assistant 当前版本的完整 MySQL 8.0+ 初始化脚本（共 46 张业务表 + 内置基础种子数据），全部脚本均支持幂等重复执行，不包含任何真实 API Key 或业务凭据。

## 方式一：一键完整初始化（推荐全新建库）

直接执行合并版脚本即可一次性完成全部表结构与内置种子数据初始化：

```bash
mysql -u<username> -p <database> < src/main/resources/db/init_all_mysql.sql
```

## 方式二：按业务模块分步初始化

如需按领域逐步建表或独立维护某模块结构，按以下顺序执行：

1. `schema/00_ai_schema_mysql.sql` — AI 模型配置、Agent、提示词模板、会话消息与调用审计（6 张表）
2. `schema/10_learning_core_schema_mysql.sql` — 公共词卡缓存、用户与角色、登录令牌、个人单词本、查词足迹、复习记录、学习活跃度、用户偏好与系统日志（15 张表）
3. `schema/20_vocabulary_plan_schema_mysql.sql` — 公共词表、导入任务、学习计划、场景单元、逐词进度、异步任务、批量词卡、语义分析与全局语义资产（20 张表）
4. `schema/30_rumination_schema_mysql.sql` — 知识反刍星系图谱、历史快照（每场景保留近 30 次变更）、场景材料、微环境探针与按句跟敲巩固（5 张表）
5. `init/00_ai_agent_seed_mysql.sql` — 内置 AI Agent 与最新 Prompt 模板种子数据
6. `init/01_system_admin_seed_mysql.sql` — 初始系统管理员账号（`admin` / `ChangeMe!2026`，首次登录后请立即修改密码）

## 后续维护与升级规范

- **保持同步**：修改任何 `schema/*.sql` 或 `init/*.sql` 后，必须同步更新一键脚本 `init_all_mysql.sql`（单元测试 `DatabaseInitSqlValidationTest` 会自动校验所有 Java `@TableName` 实体表与合并一致性）。
- **未来增量升级**：本项目已收口至统一初始化基线（`baseline-version: 1`），后续如对已上线环境做增量结构变更，可在 `db/migration/` 新增 `V2__...sql` 编号迁移脚本，并在需要时设置 `LEARNING_FLYWAY_ENABLED=true`。
- **幂等与安全**：所有建表与种子数据脚本必须可重复执行；严禁写入真实模型 API Key、生产密码或个人业务数据。

## 验证

- `DatabaseInitSqlValidationTest`：无需 Docker，自动校验全部 Java 实体表覆盖、脚本幂等性与 `init_all_mysql.sql` 同步一致性。
- `DatabaseSchemaIntegrationTest`：Docker 可用时在真实 MySQL 8.0 容器中验证分步脚本与一键脚本初始化及重复执行幂等性。
- `MapperXmlValidationTest`：解析并校验全部 MyBatis Mapper XML。
