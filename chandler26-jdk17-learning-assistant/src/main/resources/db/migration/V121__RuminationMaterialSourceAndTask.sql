-- V121: 知识反刍材料增加生成来源与异步任务幂等键关联，仅增量扩表，不改变既有业务数据。

SET @col_generation_source_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_material ADD COLUMN generation_source VARCHAR(32) NOT NULL DEFAULT ''ai'' COMMENT ''内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级'' AFTER word_count',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_material'
      AND column_name = 'generation_source'
);
PREPARE stmt_generation_source FROM @col_generation_source_sql;
EXECUTE stmt_generation_source;
DEALLOCATE PREPARE stmt_generation_source;

SET @col_task_id_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_material ADD COLUMN task_id BIGINT DEFAULT NULL COMMENT ''关联异步生成任务 ID，用于断点续跑与幂等落库'' AFTER generation_source',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_material'
      AND column_name = 'task_id'
);
PREPARE stmt_task_id FROM @col_task_id_sql;
EXECUTE stmt_task_id;
DEALLOCATE PREPARE stmt_task_id;

SET @uk_material_task_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_material ADD UNIQUE KEY uk_rumination_material_task (task_id, deleted)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_material'
      AND index_name = 'uk_rumination_material_task'
);
PREPARE stmt_uk_material_task FROM @uk_material_task_sql;
EXECUTE stmt_uk_material_task;
DEALLOCATE PREPARE stmt_uk_material_task;
