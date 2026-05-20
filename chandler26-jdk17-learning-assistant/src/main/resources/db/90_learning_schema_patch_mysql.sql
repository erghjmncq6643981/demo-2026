-- 已有数据库的补丁脚本。
-- 仅用于已经执行过旧版初始化脚本的数据库。

SET @schema_name = DATABASE();

SET @add_session_user_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_chat_session ADD COLUMN user_id BIGINT NOT NULL COMMENT ''用户 ID'' AFTER id',
        'SELECT ''ai_chat_session.user_id already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'ai_chat_session'
      AND COLUMN_NAME = 'user_id'
);
PREPARE add_session_user_stmt FROM @add_session_user_sql;
EXECUTE add_session_user_stmt;
DEALLOCATE PREPARE add_session_user_stmt;

SET @add_session_scene_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE ai_chat_session ADD COLUMN scene_code VARCHAR(64) NOT NULL DEFAULT ''english_vocabulary'' COMMENT ''学习场景编码，例如 english_vocabulary'' AFTER business_id',
        'SELECT ''ai_chat_session.scene_code already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'ai_chat_session'
      AND COLUMN_NAME = 'scene_code'
);
PREPARE add_session_scene_stmt FROM @add_session_scene_sql;
EXECUTE add_session_scene_stmt;
DEALLOCATE PREPARE add_session_scene_stmt;

SET @add_session_user_scene_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_ai_chat_session_user_scene ON ai_chat_session (user_id, scene_code, deleted, update_time)',
        'SELECT ''idx_ai_chat_session_user_scene already exists'''
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'ai_chat_session'
      AND INDEX_NAME = 'idx_ai_chat_session_user_scene'
);
PREPARE add_session_user_scene_index_stmt FROM @add_session_user_scene_index_sql;
EXECUTE add_session_user_scene_index_stmt;
DEALLOCATE PREPARE add_session_user_scene_index_stmt;

SET @add_session_scene_title_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'UPDATE ai_chat_session SET scene_code = COALESCE(NULLIF(scene_code, ''''), ''english_vocabulary'')',
        'SELECT ''ai_chat_session.scene_code normalized'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'ai_chat_session'
      AND COLUMN_NAME = 'scene_code'
);
PREPARE add_session_scene_title_stmt FROM @add_session_scene_title_sql;
EXECUTE add_session_scene_title_stmt;
DEALLOCATE PREPARE add_session_scene_title_stmt;

ALTER TABLE ai_chat_session
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户 ID',
    MODIFY COLUMN scene_code VARCHAR(64) NOT NULL COMMENT '学习场景编码，例如 english_vocabulary';

