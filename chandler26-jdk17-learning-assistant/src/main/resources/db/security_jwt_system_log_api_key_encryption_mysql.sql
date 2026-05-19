-- Spring Security + JWT, service-side system logs, and encrypted model API keys.
-- Execute this after the existing ai_agent_mysql.sql and learning_user_wordbook_review_mysql.sql.

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

ALTER TABLE ai_model_config
    MODIFY COLUMN api_key TEXT NOT NULL COMMENT 'API Key ciphertext, format enc:v1:<iv>.<ciphertext>; legacy plaintext is still readable';

CREATE TABLE IF NOT EXISTS learning_system_log (
    id BIGINT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    log_type VARCHAR(64) NOT NULL COMMENT '日志类型：auth/ai/cache/review/wordbook/error 等',
    title VARCHAR(180) NOT NULL COMMENT '日志标题',
    detail TEXT COMMENT '日志详情',
    source VARCHAR(32) NOT NULL DEFAULT 'server' COMMENT '来源：server/client',
    business_type VARCHAR(64) DEFAULT NULL COMMENT '业务类型',
    business_id VARCHAR(128) DEFAULT NULL COMMENT '业务 ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    KEY idx_learning_system_log_user_time (user_id, create_time),
    KEY idx_learning_system_log_type_time (log_type, create_time),
    KEY idx_learning_system_log_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习助手系统日志';
