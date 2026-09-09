-- 词汇大挑战日历查询索引。仅补充任务状态查询路径，不改变业务数据。
SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_ai_async_task ADD INDEX idx_learning_ai_task_calendar (owner_user_id, plan_id, task_type, status, deleted)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_ai_async_task'
      AND index_name = 'idx_learning_ai_task_calendar'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;
