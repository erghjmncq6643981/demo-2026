-- 建立 Agent 与具体模型配置的稳定绑定；旧的厂商、型号字段继续作为调用快照保留。

SET @agent_model_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_agent'
      AND column_name = 'model_config_id'
);
SET @agent_model_column_sql = IF(
    @agent_model_column_exists = 0,
    'ALTER TABLE ai_agent ADD COLUMN model_config_id BIGINT DEFAULT NULL COMMENT ''绑定的具体模型配置 ID'' AFTER welcome_message',
    'SELECT 1'
);
PREPARE agent_model_column_stmt FROM @agent_model_column_sql;
EXECUTE agent_model_column_stmt;
DEALLOCATE PREPARE agent_model_column_stmt;

-- 同一型号存在多套配置时，优先默认配置，其次按优先级和 ID 选择，避免随机绑定。
UPDATE ai_agent a
SET a.model_config_id = (
    SELECT m.id
    FROM ai_model_config m
    WHERE m.deleted = 0
      AND m.provider = a.model_provider
      AND m.model_name = a.model_name
    ORDER BY m.is_default DESC, m.sequence ASC, m.id ASC
    LIMIT 1
)
WHERE a.deleted = 0
  AND a.model_config_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM ai_model_config m
      WHERE m.deleted = 0
        AND m.provider = a.model_provider
        AND m.model_name = a.model_name
  );

SET @agent_model_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_agent'
      AND index_name = 'idx_ai_agent_model_config'
);
SET @agent_model_index_sql = IF(
    @agent_model_index_exists = 0,
    'ALTER TABLE ai_agent ADD INDEX idx_ai_agent_model_config (model_config_id, deleted)',
    'SELECT 1'
);
PREPARE agent_model_index_stmt FROM @agent_model_index_sql;
EXECUTE agent_model_index_stmt;
DEALLOCATE PREPARE agent_model_index_stmt;

SET @agent_model_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'ai_agent'
      AND constraint_name = 'fk_ai_agent_model_config'
      AND constraint_type = 'FOREIGN KEY'
);
SET @agent_model_fk_sql = IF(
    @agent_model_fk_exists = 0,
    'ALTER TABLE ai_agent ADD CONSTRAINT fk_ai_agent_model_config FOREIGN KEY (model_config_id) REFERENCES ai_model_config (id)',
    'SELECT 1'
);
PREPARE agent_model_fk_stmt FROM @agent_model_fk_sql;
EXECUTE agent_model_fk_stmt;
DEALLOCATE PREPARE agent_model_fk_stmt;
