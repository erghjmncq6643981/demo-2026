-- AI 异步任务中心：统一承接批量词卡、场景材料等可预约任务。
SET @schema_name = DATABASE();
DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;

DELIMITER $$
CREATE PROCEDURE learning_add_column_if_missing(
    IN table_name_value VARCHAR(128),
    IN column_name_value VARCHAR(128),
    IN column_definition_value VARCHAR(1000)
)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = table_name_value)
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = table_name_value AND COLUMN_NAME = column_name_value) THEN
        SET @alter_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_definition_value);
        PREPARE alter_statement FROM @alter_sql;
        EXECUTE alter_statement;
        DEALLOCATE PREPARE alter_statement;
    END IF;
END$$

CREATE PROCEDURE learning_add_index_if_missing(
    IN table_name_value VARCHAR(128),
    IN index_name_value VARCHAR(128),
    IN index_definition_value VARCHAR(1000)
)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = table_name_value)
       AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = table_name_value AND INDEX_NAME = index_name_value) THEN
        SET @index_sql = index_definition_value;
        PREPARE index_statement FROM @index_sql;
        EXECUTE index_statement;
        DEALLOCATE PREPARE index_statement;
    END IF;
END$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS learning_ai_async_task (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '任务所属用户 ID',
    task_type VARCHAR(40) NOT NULL COMMENT '任务类型：scene_material、vocabulary_card',
    task_name VARCHAR(160) NOT NULL COMMENT '任务展示名称',
    plan_id BIGINT DEFAULT NULL COMMENT '关联学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '关联场景单元 ID',
    related_job_id BIGINT DEFAULT NULL COMMENT '关联领域任务 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、partial_failed、failed、cancelled',
    execution_mode VARCHAR(30) NOT NULL DEFAULT 'immediate' COMMENT '执行方式：immediate、scheduled、low_cost_window',
    scheduled_time DATETIME DEFAULT NULL COMMENT '计划执行时间',
    priority INT NOT NULL DEFAULT 50 COMMENT '任务优先级，数字越大越优先',
    total_count INT NOT NULL DEFAULT 0 COMMENT '任务总量',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功数量',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数量',
    progress_percent INT NOT NULL DEFAULT 0 COMMENT '完成百分比',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 2 COMMENT '最大重试次数',
    payload_json JSON DEFAULT NULL COMMENT 'Worker 参数 JSON',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '最后一次失败原因',
    started_time DATETIME DEFAULT NULL COMMENT '开始执行时间',
    finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    cancelled_time DATETIME DEFAULT NULL COMMENT '取消时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_ai_task_user_status (user_id, status, deleted, update_time),
    KEY idx_learning_ai_task_schedule (status, scheduled_time, priority, deleted),
    KEY idx_learning_ai_task_plan (plan_id, unit_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 异步任务统一记录';

CALL learning_add_column_if_missing('vocabulary_card_generation_job', 'async_task_id',
    '`async_task_id` BIGINT DEFAULT NULL COMMENT ''统一 AI 异步任务 ID'' AFTER `unit_id`');
CALL learning_add_index_if_missing('vocabulary_card_generation_job', 'idx_vocabulary_card_job_async_task',
    'CREATE INDEX idx_vocabulary_card_job_async_task ON vocabulary_card_generation_job (async_task_id, deleted)');

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;
