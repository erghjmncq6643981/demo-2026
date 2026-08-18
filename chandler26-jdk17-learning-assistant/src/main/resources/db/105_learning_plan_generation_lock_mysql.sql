-- 学习计划场景材料生成租约：避免同一计划并发调用 AI 产生重复费用。
-- 适用于已执行 104_vocabulary_catalog_analysis_mysql.sql 的数据库；可重复执行。
SET @schema_name = DATABASE();
DROP PROCEDURE IF EXISTS learning_add_column_if_missing;

DELIMITER $$
CREATE PROCEDURE learning_add_column_if_missing(
    IN table_name_value VARCHAR(128),
    IN column_name_value VARCHAR(128),
    IN column_definition_value VARCHAR(1000)
)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = table_name_value)
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = @schema_name
                         AND TABLE_NAME = table_name_value
                         AND COLUMN_NAME = column_name_value) THEN
        SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_definition_value);
        PREPARE alter_statement FROM @alter_sql;
        EXECUTE alter_statement;
        DEALLOCATE PREPARE alter_statement;
    END IF;
END$$
DELIMITER ;

CALL learning_add_column_if_missing('learning_plan', 'generation_lock_token',
    '`generation_lock_token` VARCHAR(64) DEFAULT NULL COMMENT ''场景材料生成租约令牌'' AFTER `ai_session_id`');
CALL learning_add_column_if_missing('learning_plan', 'generation_lock_until',
    '`generation_lock_until` DATETIME DEFAULT NULL COMMENT ''场景材料生成租约到期时间'' AFTER `generation_lock_token`');

ALTER TABLE learning_ai_async_task
    MODIFY COLUMN task_type VARCHAR(40) NOT NULL
    COMMENT '任务类型：scene_material、vocabulary_card、vocabulary_catalog_analysis';
ALTER TABLE vocabulary_catalog_analysis_job
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'pending'
    COMMENT '状态：pending、running、completed、partial_failed、failed、cancelled';

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
