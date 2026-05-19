-- 英语词汇学习结果缓存表，执行 ai_agent_mysql.sql 后再执行本文件。

CREATE TABLE IF NOT EXISTS english_vocabulary_study_record (
    id BIGINT NOT NULL COMMENT '主键',
    term VARCHAR(128) NOT NULL COMMENT '用户输入的单词或短语',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    agent_code VARCHAR(50) NOT NULL COMMENT '使用的 Agent 编码',
    template_code VARCHAR(50) NOT NULL COMMENT '使用的提示词模板编码',
    provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    session_id BIGINT DEFAULT NULL COMMENT 'AI 对话会话 ID',
    raw_content MEDIUMTEXT NOT NULL COMMENT 'AI 原始回复内容',
    parsed_json JSON COMMENT '从 AI 回复中解析出的 JSON',
    token_usage INT DEFAULT NULL COMMENT 'Token 用量',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    lookup_count INT NOT NULL DEFAULT 1 COMMENT '查询次数',
    last_lookup_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近查询时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_english_vocabulary_normalized_term (normalized_term),
    KEY idx_english_vocabulary_agent (agent_code),
    KEY idx_english_vocabulary_session (session_id),
    KEY idx_english_vocabulary_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英语词汇学习结果缓存';
