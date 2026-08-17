-- AI 模型调用场景追踪增量脚本。
-- 适用于已经完成 90/91/92/93/94 补丁的数据库；可重复执行。

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE learning_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
    ) THEN
        SET @learning_add_column_sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_ddl);
        PREPARE learning_add_column_stmt FROM @learning_add_column_sql;
        EXECUTE learning_add_column_stmt;
        DEALLOCATE PREPARE learning_add_column_stmt;
    END IF;
END$$

CREATE PROCEDURE learning_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name
    ) THEN
        SET @learning_add_index_sql = p_index_ddl;
        PREPARE learning_add_index_stmt FROM @learning_add_index_sql;
        EXECUTE learning_add_index_stmt;
        DEALLOCATE PREPARE learning_add_index_stmt;
    END IF;
END$$

DELIMITER ;

CALL learning_add_column_if_missing('ai_model_call_record', 'invocation_scene_code',
    '`invocation_scene_code` VARCHAR(64) NOT NULL DEFAULT ''general_chat'' COMMENT ''AI 调用场景编码'' AFTER `agent_code`');

CALL learning_add_index_if_missing('ai_model_call_record', 'idx_ai_model_call_invocation_scene',
    'CREATE INDEX idx_ai_model_call_invocation_scene ON ai_model_call_record (invocation_scene_code, create_time)');

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;
