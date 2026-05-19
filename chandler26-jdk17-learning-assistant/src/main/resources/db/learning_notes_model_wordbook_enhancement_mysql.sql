-- 学习助手笔记、模型配置、单词本增强迁移。
-- 已执行过 ai_agent_mysql.sql 和 learning_user_wordbook_review_mysql.sql 的环境，请执行本文件。

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '配置名称',
    provider VARCHAR(50) NOT NULL COMMENT '供应商编码，例如 deepseek、kimi',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    base_url VARCHAR(255) NOT NULL COMMENT 'Base URL',
    chat_path VARCHAR(120) NOT NULL DEFAULT '/chat/completions' COMMENT 'Chat Completions 路径',
    api_key TEXT NOT NULL COMMENT 'API Key ciphertext, format enc:v1:<iv>.<ciphertext>; legacy plaintext is still readable',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认',
    sequence INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越小越优先',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ai_model_config_provider (provider, enabled, deleted),
    KEY idx_ai_model_config_priority (enabled, deleted, is_default, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置';

ALTER TABLE learning_wordbook_entry
    MODIFY COLUMN note TEXT DEFAULT NULL COMMENT 'Markdown 笔记';

SET @add_status_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''vague'' COMMENT ''单词状态：familiar、forgotten、vague'' AFTER note',
        'SELECT ''learning_wordbook_entry.status already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'status'
);
PREPARE add_status_stmt FROM @add_status_sql;
EXECUTE add_status_stmt;
DEALLOCATE PREPARE add_status_stmt;

SET @add_status_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_learning_wordbook_entry_status ON learning_wordbook_entry (wordbook_id, status, deleted)',
        'SELECT ''idx_learning_wordbook_entry_status already exists'''
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND INDEX_NAME = 'idx_learning_wordbook_entry_status'
);
PREPARE add_status_index_stmt FROM @add_status_index_sql;
EXECUTE add_status_index_stmt;
DEALLOCATE PREPARE add_status_index_stmt;
