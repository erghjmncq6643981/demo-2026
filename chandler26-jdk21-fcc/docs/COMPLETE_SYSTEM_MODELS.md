# 固定通话模型初始化

模型源文件为 `fcc-common/src/main/resources/flows/system-models.json`。四类模型分别为呼入、坐席先接外呼、通知外呼、话机绑定，包含入口、动作、参数或参数来源、条件后继和终态。真实号码、坐席、技能组及媒体路径不作为演示数据初始化。

这些模型描述当前固定运行器，`executionMode=FIXED_RUNTIME`，并非任意动作图解释器。自动外呼任务调度复用坐席先接或通知模型；任务抢占、重试调度不伪装成通话动作。话机绑定已有专用运行器和绑定历史，但尚未接入统一通话阶段事实记录，不能据此宣称绑定通话执行流水已完成。

## 执行顺序

- 新数据库：执行 `docs/fcc-schema.sql`，其中已包含完整模型。
- 已有数据库：先确认六个 `20260920` 迁移已执行，然后执行 `20260921_staged_flows.sql`（已执行则跳过），最后执行 `20260922_complete_system_models.sql`。
- 完整模型迁移可重复执行，前三种模型发布 v2，原 v1 归档保留；话机绑定新增 v1。执行后再启动应用。不要重复执行旧的非幂等迁移。

MySQL 命令行执行时指定 `--default-character-set=utf8mb4`，避免旧迁移中的中文名称被客户端按本地编码解释。

运行 `node tools/generate-system-models.mjs` 可重新生成本次 SQL 和基线生成区。此脚本仅用于尚未部署的模型变更；迁移部署后不得修改，后续改动新增版本迁移。

核验查询：

```sql
SELECT f.flow_key, v.version_no,
       JSON_LENGTH(v.definition_json, '$.nodes') AS action_count,
       JSON_UNQUOTE(JSON_EXTRACT(v.definition_json, '$.executionMode')) AS execution_mode
FROM fcc_flow_definition f
JOIN fcc_flow_definition_version v ON v.flow_definition_id = f.id
WHERE f.tenant_id = 0 AND f.flow_key LIKE 'SYSTEM_%'
  AND v.publish_status = 'PUBLISHED';
```

预期动作数：呼入 7、坐席先接 6、通知 5、话机绑定 4。旧通话继续引用原版本；不要回填未发生的历史动作。回退应用前停止新呼叫，保留所有版本和执行事实；模型回退应通过新发布版本完成，不删除被历史通话引用的数据。

本次已在独立 MySQL 8 测试实例验证基线初始化、旧三模型升级和完整模型迁移重复执行，升级后保留三个归档版本和四个发布版本。业务库默认连接认证失败，未对业务库执行迁移；本验证不代表业务库已初始化。
