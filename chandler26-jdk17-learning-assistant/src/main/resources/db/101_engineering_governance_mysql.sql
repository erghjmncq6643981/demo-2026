-- 工程治理增量：保证 AI 会话消息序号唯一，支持并发写入冲突重试。
-- 适用于已经执行旧版 AI 表结构的数据库，可重复执行。

SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS learning_add_chat_message_sequence_key;

DELIMITER $$

CREATE PROCEDURE learning_add_chat_message_sequence_key()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'ai_chat_message'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'ai_chat_message'
          AND INDEX_NAME = 'uk_ai_chat_message_session_sequence'
    ) THEN
        UPDATE ai_chat_message message_row
        JOIN (
            SELECT id,
                   ROW_NUMBER() OVER (
                       PARTITION BY session_id
                       ORDER BY sequence, create_time, id
                   ) AS normalized_sequence
            FROM ai_chat_message
        ) ranked ON ranked.id = message_row.id
        SET message_row.sequence = ranked.normalized_sequence;

        ALTER TABLE ai_chat_message
            ADD UNIQUE KEY uk_ai_chat_message_session_sequence (session_id, sequence);
    END IF;
END$$

DELIMITER ;

CALL learning_add_chat_message_sequence_key();

DROP PROCEDURE IF EXISTS learning_add_chat_message_sequence_key;
