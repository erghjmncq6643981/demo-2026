-- 活动统计与管理员 AI 会话摘要查询索引。仅补充查询路径，不改变业务数据。
SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_wordbook_entry ADD INDEX idx_learning_wordbook_entry_user_created (user_id, deleted, create_time)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_wordbook_entry'
      AND index_name = 'idx_learning_wordbook_entry_user_created'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;

SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_chat_message ADD INDEX idx_ai_chat_message_session_deleted_time (session_id, deleted, create_time)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_chat_message'
      AND index_name = 'idx_ai_chat_message_session_deleted_time'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;

SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_model_call_record ADD INDEX idx_ai_model_call_session_deleted_time (session_id, deleted, create_time)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_model_call_record'
      AND index_name = 'idx_ai_model_call_session_deleted_time'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;
