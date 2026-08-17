-- 96_learning_plan_date_and_status_mysql.sql
-- 适用于已有数据库的学习计划时间与状态扩展补丁

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;

DELIMITER $$

CREATE PROCEDURE learning_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @learning_add_column_sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_ddl);
        PREPARE learning_add_column_stmt FROM @learning_add_column_sql;
        EXECUTE learning_add_column_stmt;
        DEALLOCATE PREPARE learning_add_column_stmt;
    END IF;
END$$

DELIMITER ;

CALL learning_add_column_if_missing('learning_plan', 'start_time',
    '`start_time` DATETIME DEFAULT NULL COMMENT \'学习计划开始时间\' AFTER `learning_purpose`');

CALL learning_add_column_if_missing('learning_plan', 'end_time',
    '`end_time` DATETIME DEFAULT NULL COMMENT \'学习计划结束时间\' AFTER `start_time`');

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
