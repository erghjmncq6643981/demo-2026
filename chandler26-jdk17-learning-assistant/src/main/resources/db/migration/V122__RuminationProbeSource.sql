-- V122: 知识反刍微环境探针增加题目级生成来源标识，区分 AI 出题与本地语义补齐，仅增量扩表。

SET @col_probe_source_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_probe ADD COLUMN generation_source VARCHAR(32) NOT NULL DEFAULT ''ai'' COMMENT ''题目生成来源：ai 默认大模型生成，fallback 本地语义引擎补齐或降级'' AFTER hint',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_probe'
      AND column_name = 'generation_source'
);
PREPARE stmt_probe_source FROM @col_probe_source_sql;
EXECUTE stmt_probe_source;
DEALLOCATE PREPARE stmt_probe_source;
