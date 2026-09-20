# 固定通话模型初始化

`fcc-common/src/main/resources/flows/system-models.json` 是固定流程模型的唯一来源。它定义呼入、坐席先接外呼、通知外呼和话机绑定四类模型，包含入口、动作、参数来源、条件分支与终态。真实号码、坐席、技能组和媒体路径属于业务配置，不作为初始化数据写入。

这些模型使用 `executionMode=FIXED_RUNTIME`。管理端依据公共动作目录校验模型，`fcc-server` 的对应应用服务执行动作；它们不是允许任意 Java 类或脚本执行的通用工作流引擎。自动外呼调度复用坐席先接外呼或通知外呼模型，任务抢占、重试和恢复属于调度事实，不伪装成通话动作。

## 初始化

本项目不兼容旧呼叫中心数据库，不提供旧产品数据迁移，也不引入租户模型。初始化顺序如下：

1. 创建空 MySQL 8 数据库。
2. 使用 `utf8mb4` 执行 `docs/fcc-schema.sql`。
3. 配置节点、线路、号码、坐席、技能组和媒体文件。
4. 启动 `fcc-admin` 与 `fcc-server`。

基线会以 v1 发布四个完整模型。模型发生不兼容修改时，在开发阶段重新生成并重建数据库；进入生产变更管理后，再从该基线开始新增不可变版本迁移。

当前仅支持全新基线初始化。`fcc_call_session` 不包含旧 `route_mode` 字段；已有开发库如包含该字段，不在应用中读取，也不作为兼容契约。需要保留开发配置时先导出明确的配置表，再重建空库并重新导入经审查的数据，不能搬运旧项目话单或流程 JSON。

运行以下命令可以根据模型资源重新生成基线中的模型数据：

```bash
node tools/generate-system-models.mjs
```

核验查询：

```sql
SELECT f.flow_key,
       v.version_no,
       JSON_LENGTH(v.definition_json, '$.nodes') AS action_count,
       JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.executionMode')) AS execution_mode
FROM fcc_flow_definition f
JOIN fcc_flow_definition_version v ON v.flow_definition_id = f.id
WHERE f.flow_key LIKE 'SYSTEM_%'
  AND v.publish_status = 'PUBLISHED';
```

预期四条记录的版本均为 v1；动作数依次为呼入 7、坐席先接外呼 6、通知外呼 5、话机绑定 4。当前静态生成和 Java 资源加载已验证；本次没有连接用户实际 MySQL 环境执行建库。

## 执行事实边界

呼入、外呼、通知和话机绑定流程均写入通话与流程阶段事实。话机绑定的正常轨迹为 `ENTRY -> COLLECT_CODE -> VERIFY_BINDING -> END`；校验拒绝可从 `ENTRY` 直接进入 `END`。阶段记录以持久事实为准，管理端不推测缺失轨迹。
