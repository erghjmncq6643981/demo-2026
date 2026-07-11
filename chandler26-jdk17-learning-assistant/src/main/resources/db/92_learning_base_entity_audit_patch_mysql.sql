-- 学习助手 BaseEntity 审计字段补丁。
-- 适用于已经执行过旧版初始化脚本的数据库；可重复执行。

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

CREATE PROCEDURE learning_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @learning_add_index_sql = p_index_ddl;
        PREPARE learning_add_index_stmt FROM @learning_add_index_sql;
        EXECUTE learning_add_index_stmt;
        DEALLOCATE PREPARE learning_add_index_stmt;
    END IF;
END$$

DELIMITER ;

CALL learning_add_column_if_missing('ai_agent', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_agent', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_agent', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('ai_prompt_template', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_prompt_template', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_prompt_template', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('ai_chat_session', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_chat_session', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_chat_session', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('ai_chat_message', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_chat_message', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_chat_message', 'update_time', '`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`');
CALL learning_add_column_if_missing('ai_chat_message', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('ai_chat_message', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('ai_model_call_record', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_model_call_record', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_model_call_record', 'update_time', '`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`');
CALL learning_add_column_if_missing('ai_model_call_record', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('ai_model_call_record', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('ai_model_config', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('ai_model_config', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('ai_model_config', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('english_vocabulary_study_record', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('english_vocabulary_study_record', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('english_vocabulary_study_record', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('english_vocabulary_study_record', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_user', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_user', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_user', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_user', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_user_token', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_user_token', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_user_token', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_user_token', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_wordbook', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_wordbook', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_wordbook', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_wordbook_entry', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_wordbook_entry', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_wordbook_entry', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_vocabulary_tag', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_vocabulary_tag', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_vocabulary_tag', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_vocabulary_tag', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_vocabulary_relation', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_vocabulary_relation', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_vocabulary_relation', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_vocabulary_relation', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_review_record', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_review_record', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_review_record', 'update_time', '`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`');
CALL learning_add_column_if_missing('learning_review_record', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_review_record', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_user_preference', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_user_preference', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_user_preference', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_user_preference', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_column_if_missing('learning_system_log', 'create_by', '`create_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''创建人用户 ID'' AFTER `id`');
CALL learning_add_column_if_missing('learning_system_log', 'update_by', '`update_by` BIGINT NOT NULL DEFAULT 0 COMMENT ''更新人用户 ID'' AFTER `create_by`');
CALL learning_add_column_if_missing('learning_system_log', 'update_time', '`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`');
CALL learning_add_column_if_missing('learning_system_log', 'deleted', '`deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否删除'' AFTER `update_time`');
CALL learning_add_column_if_missing('learning_system_log', 'version', '`version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`');

CALL learning_add_index_if_missing('ai_chat_message', 'idx_ai_chat_message_session_deleted_sequence',
    'CREATE INDEX idx_ai_chat_message_session_deleted_sequence ON ai_chat_message (session_id, deleted, sequence)');
CALL learning_add_index_if_missing('ai_model_call_record', 'idx_ai_model_call_deleted_time',
    'CREATE INDEX idx_ai_model_call_deleted_time ON ai_model_call_record (deleted, create_time)');
CALL learning_add_index_if_missing('learning_review_record', 'idx_learning_review_record_user_deleted_time',
    'CREATE INDEX idx_learning_review_record_user_deleted_time ON learning_review_record (user_id, deleted, create_time)');
CALL learning_add_index_if_missing('learning_system_log', 'idx_learning_system_log_user_deleted_time',
    'CREATE INDEX idx_learning_system_log_user_deleted_time ON learning_system_log (user_id, deleted, create_time)');

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;
