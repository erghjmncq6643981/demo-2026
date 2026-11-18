-- =============================================================================
-- English Learning Assistant (MySQL 8.0+) 一键完整初始化脚本
-- 由 schema/00..30 与 init/00..01 按顺序合并生成（共 46 张表 + 全部内置种子数据）
-- 特点：全量幂等可重复执行，不包含任何真实 API Key 或个人业务数据。
-- 使用示例：mysql -u<user> -p <database> < src/main/resources/db/init_all_mysql.sql
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- [1/6 AI 模型配置、Agent、提示词与会话表结构] (schema/00_ai_schema_mysql.sql)
-- -----------------------------------------------------------------------------

-- AI Agent、提示词、会话与模型配置表。
-- 仅包含当前版本完整表结构；全新数据库第一个执行。

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    name VARCHAR(100) NOT NULL COMMENT '配置名称',
    provider VARCHAR(50) NOT NULL COMMENT '供应商编码，例如 deepseek、kimi',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    base_url VARCHAR(255) NOT NULL COMMENT '模型服务 Base URL',
    chat_path VARCHAR(120) NOT NULL DEFAULT '/chat/completions' COMMENT 'Chat Completions 路径',
    api_key TEXT NOT NULL COMMENT '加密 API Key，格式 enc:v1:<iv>.<ciphertext>',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认配置',
    sequence INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越小越优先',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_ai_model_config_provider (provider, enabled, deleted),
    KEY idx_ai_model_config_priority (enabled, deleted, is_default, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置';

CREATE TABLE IF NOT EXISTS ai_agent (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    name VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    code VARCHAR(50) NOT NULL COMMENT 'Agent 唯一编码',
    type VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT '类型：chat、analysis、assistant',
    icon VARCHAR(200) DEFAULT NULL COMMENT '图标编码',
    description VARCHAR(500) DEFAULT NULL COMMENT '功能描述',
    system_prompt TEXT COMMENT '完整系统提示词',
    concise_prompt TEXT COMMENT '多轮对话精简提示词',
    welcome_message VARCHAR(500) DEFAULT NULL COMMENT '欢迎语',
    model_config_id BIGINT DEFAULT NULL COMMENT '绑定的具体模型配置 ID',
    model_provider VARCHAR(50) DEFAULT NULL COMMENT '指定模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '指定模型名称',
    temperature DECIMAL(4,2) DEFAULT NULL COMMENT '温度参数',
    max_tokens INT DEFAULT NULL COMMENT '最大输出 Token 数',
    preset_commands JSON COMMENT '预设指令 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sequence INT NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_code (code),
    KEY idx_ai_agent_type (type),
    KEY idx_ai_agent_enabled (enabled, deleted, sequence),
    KEY idx_ai_agent_model_config (model_config_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    code VARCHAR(50) NOT NULL COMMENT '模板唯一编码',
    type VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '类型：system、user、analysis',
    tags VARCHAR(200) DEFAULT NULL COMMENT '标签，逗号分隔',
    content TEXT NOT NULL COMMENT '提示词内容，支持 {{variable}} 占位符',
    variables JSON COMMENT '变量定义 JSON',
    description VARCHAR(500) DEFAULT NULL COMMENT '模板描述',
    example_input TEXT COMMENT '示例输入',
    example_output TEXT COMMENT '示例输出',
    public_template TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公开',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sequence INT NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_prompt_template_code (code),
    KEY idx_ai_prompt_template_type (type),
    KEY idx_ai_prompt_template_tags (tags),
    KEY idx_ai_prompt_template_enabled (enabled, deleted, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提示词模板';

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    agent_code VARCHAR(50) NOT NULL COMMENT 'Agent 编码',
    business_type VARCHAR(50) DEFAULT NULL COMMENT '关联业务类型',
    business_id VARCHAR(128) DEFAULT NULL COMMENT '关联业务 ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '复用会话的学习场景编码',
    title VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    variables_json JSON COMMENT '会话级变量 JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_ai_chat_session_user_scene (user_id, scene_code, deleted, update_time),
    KEY idx_ai_chat_session_agent (agent_code),
    KEY idx_ai_chat_session_biz (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：system、user、assistant',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    token_count INT DEFAULT NULL COMMENT 'Token 数量',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    model_provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    sequence INT NOT NULL DEFAULT 0 COMMENT '会话内消息序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_chat_message_session_sequence (session_id, sequence),
    KEY idx_ai_chat_message_session_deleted_time (session_id, deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';

CREATE TABLE IF NOT EXISTS ai_model_call_record (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    session_id BIGINT DEFAULT NULL COMMENT '会话 ID',
    agent_code VARCHAR(50) DEFAULT NULL COMMENT 'Agent 编码',
    invocation_scene_code VARCHAR(64) NOT NULL DEFAULT 'general_chat' COMMENT 'AI 调用场景编码',
    provider VARCHAR(50) NOT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    request_json JSON COMMENT '请求 JSON，不含认证信息',
    response_json MEDIUMTEXT COMMENT '原始响应 JSON',
    success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否调用成功',
    error_message VARCHAR(2048) DEFAULT NULL COMMENT '失败信息',
    prompt_tokens INT DEFAULT NULL COMMENT '输入 Token 数',
    completion_tokens INT DEFAULT NULL COMMENT '输出 Token 数',
    total_tokens INT DEFAULT NULL COMMENT '总 Token 数',
    latency_ms BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_ai_model_call_session (session_id),
    KEY idx_ai_model_call_session_deleted_time (session_id, deleted, create_time),
    KEY idx_ai_model_call_agent (agent_code),
    KEY idx_ai_model_call_invocation_scene (invocation_scene_code, create_time),
    KEY idx_ai_model_call_provider (provider),
    KEY idx_ai_model_call_deleted_time (deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型调用记录';

-- -----------------------------------------------------------------------------
-- [2/6 用户、公共词卡、个人单词本、查词足迹、复习、活跃度与系统日志表结构] (schema/10_learning_core_schema_mysql.sql)
-- -----------------------------------------------------------------------------

-- 用户、公共词卡缓存、个人单词本、复习与系统日志表。
-- 依赖 00_ai_schema_mysql.sql。

CREATE TABLE IF NOT EXISTS english_vocabulary_study_record (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    term VARCHAR(128) NOT NULL COMMENT '标准展示单词或短语',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    agent_code VARCHAR(50) NOT NULL COMMENT '生成词卡的 Agent 编码',
    template_code VARCHAR(50) NOT NULL COMMENT '生成词卡的模板编码',
    provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    session_id BIGINT DEFAULT NULL COMMENT 'AI 对话会话 ID',
    raw_content MEDIUMTEXT NOT NULL COMMENT 'AI 原始回复',
    parsed_json JSON COMMENT '结构化词卡 JSON',
    token_usage INT DEFAULT NULL COMMENT 'Token 用量',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    lookup_count INT NOT NULL DEFAULT 1 COMMENT '缓存查询次数',
    last_lookup_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近查询时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_english_vocabulary_normalized_term (normalized_term),
    KEY idx_english_vocabulary_agent (agent_code),
    KEY idx_english_vocabulary_session (session_id),
    KEY idx_english_vocabulary_update_time (deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共英语词汇学习卡缓存';

CREATE TABLE IF NOT EXISTS learning_user (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号码',
    email VARCHAR(128) DEFAULT NULL COMMENT '联系邮箱',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    role_code VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '系统角色：USER-普通用户，ADMIN-系统管理员',
    last_login_time DATETIME DEFAULT NULL COMMENT '最近登录时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_user_username (username),
    KEY idx_learning_user_role_enabled (role_code, enabled, deleted),
    KEY idx_learning_user_update_time (deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习助手用户';

CREATE TABLE IF NOT EXISTS learning_user_token (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    token_hash VARCHAR(128) NOT NULL COMMENT '访问令牌 SHA-256 哈希',
    expired_time DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已注销',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_user_token_hash (token_hash),
    KEY idx_learning_user_token_user (user_id, deleted),
    KEY idx_learning_user_token_expired_time (expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习助手登录令牌';

CREATE TABLE IF NOT EXISTS learning_wordbook (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    name VARCHAR(64) NOT NULL COMMENT '单词本名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '单词本描述',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认单词本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_wordbook_user (user_id, deleted, update_time),
    KEY idx_learning_wordbook_default (user_id, is_default, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人学习单词本';

CREATE TABLE IF NOT EXISTS learning_wordbook_entry (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '词条所属用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '个人单词本 ID',
    progress_id BIGINT DEFAULT NULL COMMENT '用户跨词本逐词进度 ID',
    catalog_entry_id BIGINT DEFAULT NULL COMMENT '来源公共词表词条 ID',
    vocabulary_id BIGINT DEFAULT NULL COMMENT '公共词卡缓存 ID，词卡生成前可为空',
    term VARCHAR(128) NOT NULL COMMENT '展示单词或短语',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    note TEXT DEFAULT NULL COMMENT '用户 Markdown 笔记',
    snapshot_raw_content MEDIUMTEXT DEFAULT NULL COMMENT '加入单词本时的 AI 原始回复快照',
    snapshot_parsed_json JSON DEFAULT NULL COMMENT '加入单词本时的结构化词卡快照',
    snapshot_tags_json JSON DEFAULT NULL COMMENT '加入单词本时的标签快照',
    snapshot_relations_json JSON DEFAULT NULL COMMENT '加入单词本时的关联词快照',
    snapshot_provider VARCHAR(50) DEFAULT NULL COMMENT '快照模型供应商',
    snapshot_model_name VARCHAR(100) DEFAULT NULL COMMENT '快照模型名称',
    snapshot_session_id BIGINT DEFAULT NULL COMMENT '快照关联 AI 会话 ID',
    snapshot_time DATETIME DEFAULT NULL COMMENT '快照生成时间',
    card_status VARCHAR(20) NOT NULL DEFAULT 'missing' COMMENT '词卡状态：missing、queued、generating、ready、failed、not_required',
    card_error_message VARCHAR(1000) DEFAULT NULL COMMENT '词卡生成失败原因',
    card_generated_time DATETIME DEFAULT NULL COMMENT '词卡生成完成时间',
    status VARCHAR(20) NOT NULL DEFAULT 'vague' COMMENT '熟练状态：familiar、forgotten、vague',
    review_stage INT NOT NULL DEFAULT 0 COMMENT '艾宾浩斯复习阶段',
    mastery_score INT NOT NULL DEFAULT 0 COMMENT '掌握度 0-100',
    first_review_time DATETIME DEFAULT NULL COMMENT '首次复习时间',
    last_review_time DATETIME DEFAULT NULL COMMENT '最近复习时间',
    next_review_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次复习时间',
    due_count INT NOT NULL DEFAULT 0 COMMENT '进入复习队列次数',
    review_count INT NOT NULL DEFAULT 0 COMMENT '复习次数',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '记住次数',
    wrong_count INT NOT NULL DEFAULT 0 COMMENT '忘记次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_wordbook_entry_term (wordbook_id, normalized_term),
    KEY idx_learning_wordbook_entry_user_due (user_id, deleted, next_review_time),
    KEY idx_learning_wordbook_entry_vocabulary (vocabulary_id),
    KEY idx_learning_wordbook_entry_status (wordbook_id, status, deleted),
    KEY idx_learning_wordbook_entry_progress (progress_id, deleted),
    KEY idx_learning_wordbook_entry_user_created (user_id, deleted, create_time),
    KEY idx_learning_wordbook_entry_catalog (catalog_entry_id, deleted),
    KEY idx_learning_wordbook_entry_wb_due_order (user_id, wordbook_id, deleted, next_review_time, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人单词本词条与复习状态';

CREATE TABLE IF NOT EXISTS learning_vocabulary_tag (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    vocabulary_id BIGINT NOT NULL COMMENT '公共词卡缓存 ID',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    tag_type VARCHAR(50) NOT NULL COMMENT '标签类型',
    tag_value VARCHAR(128) NOT NULL COMMENT '标签值',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    weight INT NOT NULL DEFAULT 50 COMMENT '标签权重 0-100',
    source VARCHAR(50) NOT NULL DEFAULT 'parsed_json' COMMENT '标签来源',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_vocabulary_tag (vocabulary_id, tag_type, tag_value),
    KEY idx_learning_vocabulary_tag_lookup (normalized_term, tag_type, deleted),
    KEY idx_learning_vocabulary_tag_value (tag_type, tag_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词汇多维标签';

CREATE TABLE IF NOT EXISTS learning_vocabulary_relation (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    vocabulary_id BIGINT NOT NULL COMMENT '当前公共词卡缓存 ID',
    related_vocabulary_id BIGINT DEFAULT NULL COMMENT '已入库关联词卡缓存 ID',
    normalized_term VARCHAR(128) NOT NULL COMMENT '当前词归一化值',
    related_term VARCHAR(128) NOT NULL COMMENT '关联词',
    relation_type VARCHAR(50) NOT NULL COMMENT '语义关系：synonym、antonym、word_family',
    relation_value VARCHAR(128) DEFAULT NULL COMMENT '关系说明',
    related_part_of_speech VARCHAR(50) DEFAULT NULL COMMENT '关联词词性',
    related_meaning VARCHAR(512) DEFAULT NULL COMMENT '关联词核心含义',
    match_type VARCHAR(50) DEFAULT NULL COMMENT '匹配来源',
    match_score INT DEFAULT NULL COMMENT '匹配分数 0-100',
    score INT NOT NULL DEFAULT 50 COMMENT '相关度 0-100',
    source VARCHAR(50) NOT NULL DEFAULT 'parsed_json' COMMENT '关系来源',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_vocabulary_relation (normalized_term, related_term, relation_type),
    KEY idx_learning_vocabulary_relation_term (normalized_term, score, deleted),
    KEY idx_learning_vocabulary_relation_related (related_term),
    KEY idx_learning_vocabulary_relation_match (normalized_term, match_type, match_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词汇语义关联关系';

CREATE TABLE IF NOT EXISTS learning_vocabulary_alias (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    vocabulary_id BIGINT NOT NULL COMMENT '公共词卡缓存 ID',
    alias_term VARCHAR(128) NOT NULL COMMENT '变形或别名单词',
    normalized_alias VARCHAR(128) NOT NULL COMMENT '归一化别名',
    lemma VARCHAR(128) NOT NULL COMMENT '原形单词',
    normalized_lemma VARCHAR(128) NOT NULL COMMENT '归一化原形',
    alias_type VARCHAR(50) NOT NULL COMMENT '别名类型：exact, plural, past_tense, past_participle, present_participle, third_person_singular, comparative, superlative, irregular, ai_generated',
    source VARCHAR(50) NOT NULL DEFAULT 'rule' COMMENT '来源：rule, ai, manual',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_vocabulary_alias (normalized_alias, vocabulary_id),
    KEY idx_learning_vocabulary_alias_lookup (normalized_alias, deleted),
    KEY idx_learning_vocabulary_alias_vocab (vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英语词汇形态变形与别名索引';

CREATE TABLE IF NOT EXISTS learning_vocabulary_lookup_history (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '查询用户 ID',
    vocabulary_id BIGINT DEFAULT NULL COMMENT '关联公共词卡缓存 ID',
    term VARCHAR(128) NOT NULL COMMENT '展示单词或短语',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    lookup_source VARCHAR(32) NOT NULL DEFAULT 'study' COMMENT '查询来源：study-卡片查词，quick_lookup-快捷查词，wordbook_add-手动加词',
    lookup_count INT NOT NULL DEFAULT 1 COMMENT '累计查询次数',
    first_lookup_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次查询时间',
    last_lookup_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近查询时间',
    last_reviewed_time DATETIME DEFAULT NULL COMMENT '最近一次在复习流中过关时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocab_lookup_user_term (user_id, normalized_term),
    KEY idx_vocab_lookup_user_last_time (user_id, deleted, last_lookup_time),
    KEY idx_vocab_lookup_vocab (vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户个人查词与搜索历史足迹';

CREATE TABLE IF NOT EXISTS learning_review_record (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '个人单词本 ID',
    entry_id BIGINT NOT NULL COMMENT '个人单词本词条 ID',
    vocabulary_id BIGINT DEFAULT NULL COMMENT '公共词卡缓存 ID，词卡生成前可为空',
    word_progress_id BIGINT DEFAULT NULL COMMENT '用户逐词进度 ID',
    plan_id BIGINT DEFAULT NULL COMMENT '场景学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '场景学习单元 ID',
    assessment_type VARCHAR(30) DEFAULT NULL COMMENT '检查类型：meaning_choice、copy_typing、meaning_spelling',
    question_json JSON DEFAULT NULL COMMENT '题目与选项快照',
    answer_text VARCHAR(1000) DEFAULT NULL COMMENT '学习者答案',
    correct_answer VARCHAR(1000) DEFAULT NULL COMMENT '正确答案快照',
    check_result VARCHAR(20) DEFAULT NULL COMMENT '系统判定：correct、incorrect',
    typing_accuracy DECIMAL(5,2) DEFAULT NULL COMMENT '拼写准确率 0-100',
    hint_level INT DEFAULT NULL COMMENT '使用提示级别',
    attempt_count INT DEFAULT NULL COMMENT '本题尝试次数',
    duration_millis BIGINT DEFAULT NULL COMMENT '本次检查耗时毫秒',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    result VARCHAR(20) NOT NULL COMMENT '复习结果：remembered、vague、forgotten',
    score INT DEFAULT NULL COMMENT '本次自评分',
    review_stage_before INT NOT NULL COMMENT '复习前阶段',
    review_stage_after INT NOT NULL COMMENT '复习后阶段',
    mastery_before INT NOT NULL COMMENT '复习前掌握度',
    mastery_after INT NOT NULL COMMENT '复习后掌握度',
    next_review_time DATETIME NOT NULL COMMENT '下次复习时间',
    duration_seconds INT DEFAULT NULL COMMENT '本次复习耗时秒数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_review_record_user_deleted_time (user_id, deleted, create_time),
    KEY idx_learning_review_record_entry (entry_id, create_time),
    KEY idx_learning_review_record_term (normalized_term),
    KEY idx_learning_review_record_progress_type (word_progress_id, assessment_type, create_time),
    KEY idx_learning_review_record_unit_entry (unit_id, entry_id, check_result, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逐词学习与复习检查记录';

CREATE TABLE IF NOT EXISTS learning_activity_event (
    id BIGINT NOT NULL COMMENT '活动事件主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '活动所属用户 ID',
    event_type VARCHAR(40) NOT NULL COMMENT '活动类型编码',
    occurred_at DATETIME NOT NULL COMMENT '行为实际发生时间',
    plan_id BIGINT DEFAULT NULL COMMENT '关联学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '关联场景单元 ID',
    material_id BIGINT DEFAULT NULL COMMENT '关联场景材料或文章记录 ID',
    entry_id BIGINT DEFAULT NULL COMMENT '关联个人单词本词条 ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '本次行为涉及数量',
    duration_seconds INT DEFAULT NULL COMMENT '有效学习时长，单位秒',
    result_code VARCHAR(30) DEFAULT NULL COMMENT '行为结果编码',
    idempotency_key VARCHAR(180) NOT NULL COMMENT '业务幂等键',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '投影状态：pending、processing、succeeded',
    claim_token VARCHAR(64) DEFAULT NULL COMMENT '批量投影领取令牌',
    processed_time DATETIME DEFAULT NULL COMMENT '投影成功时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_activity_event_idempotency (user_id, idempotency_key),
    KEY idx_learning_activity_event_pending (status, deleted, create_time),
    KEY idx_learning_activity_event_user_time (user_id, deleted, occurred_at),
    KEY idx_learning_activity_event_business (user_id, event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户学习活动原始事件';

CREATE TABLE IF NOT EXISTS learning_activity_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动日汇总主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    activity_date DATE NOT NULL COMMENT '活动日期',
    metric_type VARCHAR(40) NOT NULL COMMENT '日汇总指标编码',
    metric_count INT NOT NULL DEFAULT 0 COMMENT '指标数量',
    duration_seconds INT NOT NULL DEFAULT 0 COMMENT '有效时长，单位秒',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '正确数量',
    total_count INT NOT NULL DEFAULT 0 COMMENT '尝试总数量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_activity_daily_user_date_metric (user_id, activity_date, metric_type),
    KEY idx_learning_activity_daily_user_date (user_id, deleted, activity_date),
    KEY idx_learning_activity_daily_metric_date (metric_type, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户学习活动日汇总读模型';

CREATE TABLE IF NOT EXISTS learning_user_preference (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    preference_key VARCHAR(80) NOT NULL COMMENT '偏好键，例如 speech.voice_type',
    preference_value TEXT DEFAULT NULL COMMENT '偏好值',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_user_preference (user_id, preference_key),
    KEY idx_learning_user_preference_user (user_id, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好设置';

CREATE TABLE IF NOT EXISTS learning_system_log (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    log_type VARCHAR(64) NOT NULL COMMENT '日志类型：auth、ai、cache、review、wordbook、error 等',
    title VARCHAR(180) NOT NULL COMMENT '业务可读日志标题',
    detail TEXT COMMENT '日志详情',
    source VARCHAR(32) NOT NULL DEFAULT 'server' COMMENT '来源：server、client',
    business_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型',
    business_id VARCHAR(128) DEFAULT NULL COMMENT '关联业务 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_system_log_user_deleted_time (user_id, deleted, create_time),
    KEY idx_learning_system_log_type_time (log_type, create_time),
    KEY idx_learning_system_log_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习助手系统日志';

CREATE TABLE IF NOT EXISTS learning_system_log_outbox (
    id BIGINT NOT NULL COMMENT '事件主键，同时作为最终系统日志主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '日志归属用户 ID',
    log_type VARCHAR(64) NOT NULL COMMENT '日志类型',
    title VARCHAR(180) NOT NULL COMMENT '业务可读日志标题',
    detail TEXT COMMENT '有长度边界的日志详情',
    source VARCHAR(32) NOT NULL COMMENT '来源：server、client',
    business_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型',
    business_id VARCHAR(128) DEFAULT NULL COMMENT '关联业务 ID',
    occurred_at DATETIME NOT NULL COMMENT '业务动作发生时间',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '请求链路标识',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '投递状态：pending、processing、succeeded',
    claim_token VARCHAR(64) DEFAULT NULL COMMENT '异步投递领取令牌',
    processed_time DATETIME DEFAULT NULL COMMENT '成功写入最终系统日志表时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_system_log_outbox_status (status, deleted, create_time),
    KEY idx_learning_system_log_outbox_claim (claim_token, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志可靠异步投递事件';

-- -----------------------------------------------------------------------------
-- [3/6 公共词表、学习计划、场景单元、异步任务与语义资产表结构] (schema/20_vocabulary_plan_schema_mysql.sql)
-- -----------------------------------------------------------------------------

-- 公共词表、学习计划、场景单元、逐词进度与批量词卡任务表。
-- 依赖 10_learning_core_schema_mysql.sql。

CREATE TABLE IF NOT EXISTS vocabulary_catalog (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    owner_user_id BIGINT NOT NULL COMMENT '词表所属用户 ID，公共词表可使用系统用户',
    name VARCHAR(120) NOT NULL COMMENT '词表名称',
    learning_purpose VARCHAR(500) DEFAULT NULL COMMENT '学习目的，例如自考、四级、六级或雅思',
    exam_type VARCHAR(50) DEFAULT NULL COMMENT '数据源类型或考试场景编码',
    latest_version_id BIGINT DEFAULT NULL COMMENT '当前发布版本 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft、published、archived',
    visibility VARCHAR(20) NOT NULL DEFAULT 'private' COMMENT '可见范围：private、shared、public',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_vocabulary_catalog_owner (owner_user_id, deleted, update_time),
    KEY idx_vocabulary_catalog_status (status, visibility, deleted),
    KEY idx_vocabulary_catalog_public_list (status, visibility, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可导入并生成学习计划的词表';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_version (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '词表 ID',
    version_no INT NOT NULL COMMENT '版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'reviewing' COMMENT '状态：reviewing、published、superseded',
    source_format VARCHAR(20) NOT NULL COMMENT '来源格式：markdown、csv、excel、json',
    source_file_name VARCHAR(255) DEFAULT NULL COMMENT '来源文件名',
    source_hash VARCHAR(128) NOT NULL COMMENT '来源内容哈希',
    total_count INT NOT NULL DEFAULT 0 COMMENT '词条总数',
    warning_count INT NOT NULL DEFAULT 0 COMMENT '疑似断词等警告数',
    reviewed_warning_count INT NOT NULL DEFAULT 0 COMMENT '已人工确认警告数',
    published_time DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_catalog_version (catalog_id, version_no),
    KEY idx_vocabulary_catalog_version_status (catalog_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词表导入版本';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_entry (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '词表 ID',
    catalog_version_id BIGINT NOT NULL COMMENT '词表版本 ID',
    source_order INT NOT NULL COMMENT '来源序号，导入后只保留这一列序号',
    original_term VARCHAR(255) NOT NULL COMMENT '来源文件中的原始单词或短语',
    normalized_term VARCHAR(255) NOT NULL COMMENT '确认后用于去重和匹配的归一化词',
    suggested_term VARCHAR(255) DEFAULT NULL COMMENT '系统对疑似断词给出的修正建议',
    approved_term VARCHAR(255) DEFAULT NULL COMMENT '人工确认后的展示词',
    phonetic VARCHAR(255) DEFAULT NULL COMMENT '来源音标',
    definition_text TEXT DEFAULT NULL COMMENT '来源释义',
    warning_codes JSON DEFAULT NULL COMMENT '导入警告编码数组',
    suspicious TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为疑似断词',
    review_status VARCHAR(20) NOT NULL DEFAULT 'not_required' COMMENT '审核状态：not_required、pending、confirmed',
    published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已随版本发布',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_catalog_entry_order (catalog_version_id, source_order),
    KEY idx_vocabulary_catalog_entry_warning (catalog_version_id, suspicious, review_status, deleted),
    KEY idx_vocabulary_catalog_entry_term (catalog_version_id, normalized_term, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词表版本词条';

CREATE TABLE IF NOT EXISTS vocabulary_import_job (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '发起导入的用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '词表 ID',
    catalog_version_id BIGINT NOT NULL COMMENT '本次导入生成的版本 ID',
    source_format VARCHAR(20) NOT NULL COMMENT '来源格式',
    source_file_name VARCHAR(255) DEFAULT NULL COMMENT '来源文件名',
    status VARCHAR(20) NOT NULL COMMENT '状态：parsing、reviewing、published、failed',
    total_count INT NOT NULL DEFAULT 0 COMMENT '识别到的词条总数',
    warning_count INT NOT NULL DEFAULT 0 COMMENT '疑似断词等警告数',
    reviewed_warning_count INT NOT NULL DEFAULT 0 COMMENT '已人工确认警告数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    finished_time DATETIME DEFAULT NULL COMMENT '解析或发布完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_vocabulary_import_user_time (user_id, deleted, update_time),
    KEY idx_vocabulary_import_status (user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词表导入任务';

CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '学习者用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '学习词表 ID',
    catalog_version_id BIGINT NOT NULL COMMENT '学习词表版本 ID',
    wordbook_id BIGINT NOT NULL COMMENT '承载个人词条快照的单词本 ID',
    name VARCHAR(120) NOT NULL COMMENT '学习计划名称',
    learning_purpose VARCHAR(500) DEFAULT NULL COMMENT '学习目的，供 AI 判断认识或拼写要求',
    start_time DATETIME DEFAULT NULL COMMENT '学习计划开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '学习计划结束时间',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active、completed、paused',
    total_catalog_words INT NOT NULL DEFAULT 0 COMMENT '词表总词数',
    learned_core_words INT NOT NULL DEFAULT 0 COMMENT '已完成首次核心学习的词数',
    completed_unit_count INT NOT NULL DEFAULT 0 COMMENT '已完成场景单元数',
    current_unit_id BIGINT DEFAULT NULL COMMENT '当前进行中的单元 ID',
    ai_session_id BIGINT DEFAULT NULL COMMENT '场景计划复用的 AI 审计会话 ID，不作为模型历史上下文',
    generation_lock_token VARCHAR(64) DEFAULT NULL COMMENT '场景材料生成租约令牌',
    generation_lock_until DATETIME DEFAULT NULL COMMENT '场景材料生成租约到期时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_plan_user_status (user_id, status, deleted, update_time),
    KEY idx_learning_plan_catalog (catalog_version_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词表场景化学习计划';

CREATE TABLE IF NOT EXISTS learning_plan_unit (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    plan_id BIGINT NOT NULL COMMENT '学习计划 ID',
    unit_no INT NOT NULL COMMENT '计划内单元序号',
    title VARCHAR(160) NOT NULL COMMENT '场景标题，例如周末大扫除',
    scenario_type VARCHAR(80) DEFAULT NULL COMMENT '场景分类',
    summary VARCHAR(1000) DEFAULT NULL COMMENT '场景简介',
    status VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态：ready、in_progress、completed',
    core_word_count INT NOT NULL DEFAULT 0 COMMENT '核心学习词数',
    extended_word_count INT NOT NULL DEFAULT 0 COMMENT '场景扩展词数',
    supplementary_word_count INT NOT NULL DEFAULT 0 COMMENT 'AI 补充词数',
    completed_core_count INT NOT NULL DEFAULT 0 COMMENT '已通过检查的核心词数',
    scene_material_id BIGINT DEFAULT NULL COMMENT '场景材料 ID',
    recommended_date DATE DEFAULT NULL COMMENT '建议学习日期，仅用于展示不作为后端限制',
    generated_time DATETIME DEFAULT NULL COMMENT 'AI 生成时间',
    started_time DATETIME DEFAULT NULL COMMENT '开始学习时间',
    completed_time DATETIME DEFAULT NULL COMMENT '完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_plan_unit_no (plan_id, unit_no),
    KEY idx_learning_plan_unit_status (plan_id, status, deleted),
    KEY idx_learning_plan_unit_calendar (plan_id, recommended_date, deleted, unit_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景学习单元';

CREATE TABLE IF NOT EXISTS learning_word_progress (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '学习者用户 ID',
    term VARCHAR(255) NOT NULL COMMENT '最近一次展示词',
    normalized_term VARCHAR(255) NOT NULL COMMENT '跨词本共享的归一化词',
    learning_state VARCHAR(20) NOT NULL DEFAULT 'unseen' COMMENT '状态：unseen、exposed、learning、reviewing、mastered',
    mastery_requirement VARCHAR(20) NOT NULL DEFAULT 'recognition' COMMENT '有效掌握要求：recognition、spelling',
    recognition_score INT NOT NULL DEFAULT 0 COMMENT '认读掌握度 0-100',
    recognition_stage INT NOT NULL DEFAULT 0 COMMENT '认读复习阶段',
    recognition_due_time DATETIME DEFAULT NULL COMMENT '认读下次复习时间',
    recognition_correct_count INT NOT NULL DEFAULT 0 COMMENT '认读答对次数',
    recognition_wrong_count INT NOT NULL DEFAULT 0 COMMENT '认读答错次数',
    spelling_score INT NOT NULL DEFAULT 0 COMMENT '拼写掌握度 0-100',
    spelling_stage INT NOT NULL DEFAULT 0 COMMENT '拼写复习阶段',
    spelling_due_time DATETIME DEFAULT NULL COMMENT '拼写下次复习时间',
    spelling_correct_count INT NOT NULL DEFAULT 0 COMMENT '拼写答对次数',
    spelling_wrong_count INT NOT NULL DEFAULT 0 COMMENT '拼写答错次数',
    exposure_count INT NOT NULL DEFAULT 0 COMMENT '在场景材料中出现次数',
    scene_count INT NOT NULL DEFAULT 0 COMMENT '出现过的不同场景数',
    latest_plan_id BIGINT DEFAULT NULL COMMENT '最近学习计划 ID',
    latest_unit_id BIGINT DEFAULT NULL COMMENT '最近学习单元 ID',
    card_status VARCHAR(20) NOT NULL DEFAULT 'missing' COMMENT '词卡状态：missing、queued、generating、ready、failed、not_required',
    last_learned_time DATETIME DEFAULT NULL COMMENT '最近学习时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_word_progress_term (user_id, normalized_term),
    KEY idx_learning_word_progress_recognition_due (user_id, recognition_due_time, deleted),
    KEY idx_learning_word_progress_spelling_due (user_id, spelling_due_time, deleted),
    KEY idx_learning_word_progress_state (user_id, learning_state, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户跨词本逐词学习进度';

CREATE TABLE IF NOT EXISTS learning_plan_unit_entry (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    plan_id BIGINT NOT NULL COMMENT '学习计划 ID',
    unit_id BIGINT NOT NULL COMMENT '场景学习单元 ID',
    catalog_entry_id BIGINT DEFAULT NULL COMMENT '来源词表词条 ID，AI 补充词为空',
    wordbook_entry_id BIGINT DEFAULT NULL COMMENT '个人单词本词条 ID',
    word_progress_id BIGINT NOT NULL COMMENT '用户逐词进度 ID',
    source_order INT DEFAULT NULL COMMENT '来源词表序号',
    term VARCHAR(255) NOT NULL COMMENT '展示单词或短语',
    normalized_term VARCHAR(255) NOT NULL COMMENT '归一化词',
    phonetic VARCHAR(255) DEFAULT NULL COMMENT '音标',
    meaning_text TEXT DEFAULT NULL COMMENT '基础释义',
    context_meaning VARCHAR(1000) DEFAULT NULL COMMENT '本场景句子或文章中的含义',
    tier VARCHAR(20) NOT NULL COMMENT '层级：core、extended、supplementary、review',
    mastery_requirement VARCHAR(20) NOT NULL DEFAULT 'recognition' COMMENT '要求：recognition、spelling',
    accepted_spellings_json JSON DEFAULT NULL COMMENT '可接受拼写、英美变体和连字符形式',
    assessment_json JSON DEFAULT NULL COMMENT '本场景词汇检查题目与答案',
    first_learning TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该词首次核心学习场景',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '单元内排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_plan_unit_term (unit_id, normalized_term),
    KEY idx_learning_plan_unit_entry_tier (unit_id, tier, sort_order, deleted),
    KEY idx_learning_plan_unit_entry_progress (word_progress_id, deleted),
    KEY idx_learning_plan_core_catalog (plan_id, catalog_entry_id, tier, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景单元词汇';

CREATE TABLE IF NOT EXISTS learning_scene_material (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '学习者用户 ID',
    plan_id BIGINT NOT NULL COMMENT '学习计划 ID',
    unit_id BIGINT NOT NULL COMMENT '场景学习单元 ID',
    revision_no INT NOT NULL DEFAULT 1 COMMENT '单元内材料版本号，从 1 递增',
    material_status VARCHAR(20) NOT NULL DEFAULT 'published' COMMENT '材料状态：draft、published、archived、failed',
    current_version TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否为当前生效版本',
    supersedes_material_id BIGINT DEFAULT NULL COMMENT '被当前版本替换的上一材料 ID',
    session_id BIGINT DEFAULT NULL COMMENT '复用的场景学习 AI 会话 ID',
    title VARCHAR(160) NOT NULL COMMENT '场景标题',
    scenario_type VARCHAR(80) DEFAULT NULL COMMENT '场景分类',
    learning_text MEDIUMTEXT DEFAULT NULL COMMENT '场景英文句子或文章',
    translation MEDIUMTEXT DEFAULT NULL COMMENT '中文译文',
    raw_content MEDIUMTEXT DEFAULT NULL COMMENT 'AI 原始回复',
    parsed_json JSON DEFAULT NULL COMMENT '场景、词汇标注和检查题 JSON',
    provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    token_usage INT DEFAULT NULL COMMENT 'Token 用量',
    cost_time BIGINT DEFAULT NULL COMMENT '模型调用耗时毫秒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_scene_material_revision (unit_id, revision_no),
    KEY idx_learning_scene_material_plan (plan_id, deleted, update_time),
    KEY idx_learning_scene_material_current (unit_id, current_version, deleted),
    KEY idx_learning_scene_material_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 场景学习材料';

CREATE TABLE IF NOT EXISTS learning_scene_related_word (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID', user_id BIGINT NOT NULL COMMENT '学习者用户 ID',
    plan_id BIGINT NOT NULL COMMENT '学习计划 ID', unit_id BIGINT NOT NULL COMMENT '场景学习单元 ID',
    scene_material_id BIGINT NOT NULL COMMENT '所属场景材料版本 ID', term VARCHAR(255) NOT NULL COMMENT '场景相关单词或短语',
    normalized_term VARCHAR(255) NOT NULL COMMENT '归一化词', phonetic VARCHAR(255) DEFAULT NULL COMMENT '音标',
    meaning_text VARCHAR(1000) DEFAULT NULL COMMENT '基础释义', context_meaning VARCHAR(1000) DEFAULT NULL COMMENT '当前场景下的释义',
    category_code VARCHAR(50) DEFAULT NULL COMMENT '场景类别编码', category_name VARCHAR(80) DEFAULT NULL COMMENT '场景类别名称',
    source_type VARCHAR(20) NOT NULL DEFAULT 'ai' COMMENT '来源：ai、manual', sort_order INT NOT NULL DEFAULT 0 COMMENT '材料内展示顺序',
    promoted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已加入当前场景挑战', promoted_entry_id BIGINT DEFAULT NULL COMMENT '提升后的场景词条 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_scene_related_material_term (scene_material_id, normalized_term),
    KEY idx_scene_related_unit (unit_id, deleted, sort_order), KEY idx_scene_related_plan (plan_id, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具体材料版本的场景相关词汇';

CREATE TABLE IF NOT EXISTS learning_scene_material_note (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '学习者用户 ID',
    plan_id BIGINT NOT NULL COMMENT '学习计划 ID',
    unit_id BIGINT NOT NULL COMMENT '场景学习单元 ID',
    scene_material_id BIGINT NOT NULL COMMENT '场景材料 ID',
    content MEDIUMTEXT DEFAULT NULL COMMENT 'Markdown 笔记正文',
    content_format VARCHAR(20) NOT NULL DEFAULT 'markdown' COMMENT '笔记格式：markdown',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_scene_material_note_user_material (user_id, scene_material_id, deleted),
    KEY idx_learning_scene_material_note_material (scene_material_id, deleted),
    KEY idx_learning_scene_material_note_plan (plan_id, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习者场景材料 Markdown 笔记';

CREATE TABLE IF NOT EXISTS learning_ai_async_task (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '兼容字段：任务成果归属用户 ID',
    owner_user_id BIGINT NOT NULL COMMENT '任务成果归属用户 ID',
    trigger_user_id BIGINT DEFAULT NULL COMMENT '任务触发用户 ID，系统触发时为空',
    operator_user_id BIGINT DEFAULT NULL COMMENT '最近一次继续或干预的用户 ID',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '触发来源：user、admin、system',
    visibility VARCHAR(20) NOT NULL DEFAULT 'owner_admin' COMMENT '可见范围：owner_admin、admin',
    task_type VARCHAR(40) NOT NULL COMMENT '任务类型：场景材料、相关词、单词词卡、词本分析、语境精读、音频',
    task_name VARCHAR(160) NOT NULL COMMENT '任务展示名称',
    plan_id BIGINT DEFAULT NULL COMMENT '关联学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '关联场景单元 ID',
    related_job_id BIGINT DEFAULT NULL COMMENT '关联领域任务 ID',
    business_type VARCHAR(50) DEFAULT NULL COMMENT '关联业务对象类型',
    business_id VARCHAR(100) DEFAULT NULL COMMENT '关联业务对象 ID',
    idempotency_key VARCHAR(160) DEFAULT NULL COMMENT '有效任务业务幂等键',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、retry_wait、completed、partial_failed、attention_required、failed、cancelled',
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
    KEY idx_learning_ai_task_owner_status (owner_user_id, status, deleted, update_time),
    KEY idx_learning_ai_task_calendar (owner_user_id, plan_id, task_type, status, deleted),
    KEY idx_learning_ai_task_schedule (status, scheduled_time, priority, deleted),
    KEY idx_learning_ai_task_plan (plan_id, unit_id, deleted),
    KEY idx_learning_ai_task_idempotency (idempotency_key, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 异步任务统一记录';

CREATE TABLE IF NOT EXISTS learning_ai_async_task_step (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID', task_id BIGINT NOT NULL COMMENT '父任务 ID',
    step_code VARCHAR(60) NOT NULL COMMENT '任务内稳定步骤编码', step_name VARCHAR(100) NOT NULL COMMENT '面向用户的步骤名称',
    step_order INT NOT NULL COMMENT '执行顺序', status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '步骤状态',
    completed_count INT NOT NULL DEFAULT 0 COMMENT '完成项数', total_count INT NOT NULL DEFAULT 1 COMMENT '总项数',
    checkpoint_json JSON DEFAULT NULL COMMENT '断点续跑检查点', lease_token VARCHAR(64) DEFAULT NULL COMMENT '执行租约令牌',
    lease_until DATETIME DEFAULT NULL COMMENT '租约到期时间', heartbeat_time DATETIME DEFAULT NULL COMMENT '最近心跳时间',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已执行次数', max_attempt_count INT NOT NULL DEFAULT 3 COMMENT '最大执行次数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '最近失败摘要', started_time DATETIME DEFAULT NULL COMMENT '首次开始时间',
    finished_time DATETIME DEFAULT NULL COMMENT '结束时间', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_ai_task_step (task_id, step_code),
    KEY idx_ai_task_step_status (task_id, status, step_order, deleted), KEY idx_ai_task_step_lease (status, lease_until, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 异步任务可恢复步骤';

CREATE TABLE IF NOT EXISTS learning_ai_async_task_attempt (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID', task_id BIGINT NOT NULL COMMENT '父任务 ID',
    step_id BIGINT NOT NULL COMMENT '任务步骤 ID', operator_user_id BIGINT DEFAULT NULL COMMENT '操作者用户 ID',
    attempt_no INT NOT NULL COMMENT '步骤内尝试序号', status VARCHAR(20) NOT NULL COMMENT '执行状态',
    model_config_id BIGINT DEFAULT NULL COMMENT '模型配置 ID', provider VARCHAR(50) DEFAULT NULL COMMENT '模型服务商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称', model_call_record_id BIGINT DEFAULT NULL COMMENT 'AI 调用审计记录 ID',
    prompt_tokens INT DEFAULT NULL COMMENT '输入 Token 数', completion_tokens INT DEFAULT NULL COMMENT '输出 Token 数',
    total_tokens INT DEFAULT NULL COMMENT '总 Token 数', cost_time BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    error_code VARCHAR(80) DEFAULT NULL COMMENT '错误编码', error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误摘要',
    started_time DATETIME NOT NULL COMMENT '开始时间', finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_ai_task_step_attempt (step_id, attempt_no),
    KEY idx_ai_task_attempt_task (task_id, create_time, deleted), KEY idx_ai_task_attempt_model_call (model_call_record_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 任务步骤执行尝试审计';

CREATE TABLE IF NOT EXISTS vocabulary_card_generation_job (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '任务发起用户 ID',
    plan_id BIGINT DEFAULT NULL COMMENT '学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '按需预生成的场景单元 ID',
    async_task_id BIGINT DEFAULT NULL COMMENT '统一 AI 异步任务 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、partial_failed、failed、cancelled',
    batch_size INT NOT NULL DEFAULT 15 COMMENT '每次模型调用词数',
    total_count INT NOT NULL DEFAULT 0 COMMENT '去重且缓存未命中的词数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '生成成功词数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '生成失败词数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '任务级失败原因',
    started_time DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_vocabulary_card_job_user (user_id, status, deleted, update_time),
    KEY idx_vocabulary_card_job_unit (unit_id, deleted),
    KEY idx_vocabulary_card_job_async_task (async_task_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批量 AI 词卡生成任务';

CREATE TABLE IF NOT EXISTS vocabulary_card_generation_job_item (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    job_id BIGINT NOT NULL COMMENT '词卡生成任务 ID',
    word_progress_id BIGINT DEFAULT NULL COMMENT '用户逐词进度 ID',
    wordbook_entry_id BIGINT DEFAULT NULL COMMENT '需要写回个人快照的词条 ID',
    term VARCHAR(255) NOT NULL COMMENT '展示词',
    normalized_term VARCHAR(255) NOT NULL COMMENT '归一化词',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、generating、completed、failed、cache_hit',
    vocabulary_id BIGINT DEFAULT NULL COMMENT '公共词卡缓存 ID',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数，仅失败词重试',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '本词失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_card_job_item (job_id, normalized_term),
    KEY idx_vocabulary_card_job_item_status (job_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批量 AI 词卡生成任务明细';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_analysis_job (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '发起分析的用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '公共词本 ID',
    catalog_version_id BIGINT NOT NULL COMMENT '待分析词本版本 ID',
    async_task_id BIGINT DEFAULT NULL COMMENT '统一 AI 异步任务 ID',
    analysis_version INT NOT NULL COMMENT '词本版本内分析修订号',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、partial_failed、failed、cancelled',
    batch_size INT NOT NULL DEFAULT 100 COMMENT '每次模型调用词条数',
    total_count INT NOT NULL DEFAULT 0 COMMENT '待分析词条总数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '已完成词条数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败词条数',
    group_count INT NOT NULL DEFAULT 0 COMMENT '已生成语义分组数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '任务级错误信息',
    started_time DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_catalog_analysis_version (catalog_version_id, analysis_version),
    KEY idx_vocabulary_catalog_analysis_status (catalog_version_id, status, deleted),
    KEY idx_vocabulary_catalog_analysis_user (user_id, update_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本语义索引分析任务';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_analysis_batch (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    job_id BIGINT NOT NULL COMMENT '关联分析任务 ID',
    batch_no INT NOT NULL COMMENT '任务内批次序号',
    entry_count INT NOT NULL DEFAULT 0 COMMENT '批次词条数',
    entry_ids_json JSON NOT NULL COMMENT '批次词条 ID 列表',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、failed',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '批次错误信息',
    started_time DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_catalog_analysis_batch (job_id, batch_no),
    KEY idx_vocabulary_catalog_analysis_batch_status (job_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本语义分析批次';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_entry_analysis (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    job_id BIGINT NOT NULL COMMENT '关联分析任务 ID',
    catalog_id BIGINT NOT NULL COMMENT '公共词本 ID',
    catalog_version_id BIGINT NOT NULL COMMENT '公共词本版本 ID',
    catalog_entry_id BIGINT NOT NULL COMMENT '词本词条 ID',
    primary_group_code VARCHAR(100) DEFAULT NULL COMMENT '主语义分组编码',
    primary_group_name VARCHAR(160) DEFAULT NULL COMMENT '主语义分组名称',
    domain_code VARCHAR(80) DEFAULT NULL COMMENT '领域编码',
    sub_topic_code VARCHAR(100) DEFAULT NULL COMMENT '子主题编码',
    tags_json JSON DEFAULT NULL COMMENT '语义标签 JSON 数组',
    related_entry_ids_json JSON DEFAULT NULL COMMENT '同批主题相关词条 ID JSON 数组',
    difficulty_level VARCHAR(30) DEFAULT NULL COMMENT '难度建议',
    confidence DECIMAL(5,4) DEFAULT NULL COMMENT '分析置信度',
    status VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态：ready、low_confidence、failed',
    source VARCHAR(20) NOT NULL DEFAULT 'ai' COMMENT '结果来源：ai、manual、rule',
    analysis_version INT NOT NULL COMMENT '分析修订号',
    raw_result_json JSON DEFAULT NULL COMMENT '受控长度的单词分析结果',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_catalog_entry_analysis (job_id, catalog_entry_id),
    KEY idx_vocabulary_catalog_entry_analysis_version (catalog_version_id, status, deleted),
    KEY idx_vocabulary_catalog_entry_analysis_group (catalog_version_id, primary_group_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本词条语义索引结果';

CREATE TABLE IF NOT EXISTS vocabulary_semantic_asset (
    id BIGINT NOT NULL COMMENT '全局语义资产主键',
    language VARCHAR(16) NOT NULL DEFAULT 'en' COMMENT '词汇语言',
    normalized_term VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '标准词，跨词本唯一',
    semantic_json JSON NOT NULL COMMENT '不含词本身份的可复用语义结果',
    related_terms_json JSON NOT NULL COMMENT '相关标准词数组',
    source_job_id BIGINT NOT NULL COMMENT '最近生成该结果的任务 ID',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_semantic_term (language, normalized_term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨词本全局词汇语义资产';

-- -----------------------------------------------------------------------------
-- [4/6 知识反刍星系图谱、历史快照、场景材料、微探针与跟敲表结构] (schema/30_rumination_schema_mysql.sql)
-- -----------------------------------------------------------------------------

-- 知识反刍场景星系图谱、词袋与巩固记录表。
-- 依赖 20_vocabulary_plan_schema_mysql.sql。

CREATE TABLE IF NOT EXISTS learning_rumination_graph (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '个人单词本 ID',
    scenario_code VARCHAR(50) NOT NULL COMMENT '场景编码',
    graph_nodes_json JSON NOT NULL COMMENT '当前场景星系节点（包含裂变与定制节点）',
    graph_edges_json JSON NOT NULL COMMENT '当前场景星系连线关系',
    bag_terms_json JSON DEFAULT NULL COMMENT '当前词袋中所选词汇列表',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rumination_graph_user_wb_scenario (user_id, wordbook_id, scenario_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍场景星系图谱拓扑与词袋状态';

-- 知识反刍场景材料文章表
CREATE TABLE IF NOT EXISTS learning_rumination_material (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '个人单词本 ID',
    scenario_code VARCHAR(50) NOT NULL COMMENT '场景编码',
    title VARCHAR(200) NOT NULL COMMENT '场景文章标题',
    scenario_summary VARCHAR(500) DEFAULT NULL COMMENT '场景语境背景概述',
    article_content MEDIUMTEXT NOT NULL COMMENT '英文原文正文',
    translation_content MEDIUMTEXT DEFAULT NULL COMMENT '中文对照翻译',
    target_words_json JSON NOT NULL COMMENT '本次反刍所选目标词汇列表',
    word_count INT DEFAULT 0 COMMENT '正文字数',
    generation_source VARCHAR(32) NOT NULL DEFAULT 'ai' COMMENT '内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级',
    task_id BIGINT DEFAULT NULL COMMENT '关联异步生成任务 ID，用于断点续跑与幂等落库',
    audio_path VARCHAR(255) DEFAULT NULL COMMENT 'AI 合成语音音频存储相对路径',
    audio_status VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT '语音合成状态：none 未生成，pending 排队中，running 合成中，completed 已就绪，failed 失败',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rumination_material_task (task_id, deleted),
    KEY idx_rumination_material_user_wb_scenario (user_id, wordbook_id, scenario_code, deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍场景材料文章';

-- 知识反刍微环境探针题库与作答表
CREATE TABLE IF NOT EXISTS learning_rumination_probe (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    material_id BIGINT NOT NULL COMMENT '关联的场景材料文章 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    target_word VARCHAR(100) NOT NULL COMMENT '考察核心词汇',
    probe_type VARCHAR(50) NOT NULL COMMENT '探针题型：context_match/collocation/causality',
    question VARCHAR(500) NOT NULL COMMENT '题干描述',
    options_json JSON NOT NULL COMMENT '选项列表 JSON',
    correct_answer VARCHAR(200) NOT NULL COMMENT '正确选项文本',
    hint VARCHAR(300) DEFAULT NULL COMMENT '情境线索提示',
    generation_source VARCHAR(32) NOT NULL DEFAULT 'ai' COMMENT '题目生成来源：ai 默认大模型生成，fallback 本地语义引擎补齐或降级',
    selected_answer VARCHAR(200) DEFAULT NULL COMMENT '用户选择答案',
    is_correct TINYINT(1) DEFAULT NULL COMMENT '作答是否正确',
    score INT DEFAULT 0 COMMENT '获得积分',
    illuminated TINYINT(1) NOT NULL DEFAULT 0 COMMENT '星系网络是否已点亮',
    answered_time DATETIME DEFAULT NULL COMMENT '作答时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_rumination_probe_material_user (material_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍微环境探针题库与作答记录';

-- 知识反刍阅读后按句跟敲与目标词遮挡补全巩固记录表
CREATE TABLE IF NOT EXISTS learning_rumination_sentence_practice (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '所属个人单词本 ID',
    material_id BIGINT NOT NULL COMMENT '关联场景材料文章 ID',
    sentence_index INT NOT NULL COMMENT '场景材料正文中的句子序号（从 0 开始）',
    target_word VARCHAR(128) NOT NULL COMMENT '归一化目标词汇原词',
    surface_form VARCHAR(128) NOT NULL COMMENT '句子语境中目标词实际展示词形',
    sentence_text VARCHAR(1000) NOT NULL COMMENT '对应英文句子原文快照',
    copy_count INT NOT NULL DEFAULT 0 COMMENT '原句跟敲完成次数（仅计入练习次数）',
    last_copy_accuracy DECIMAL(5,2) DEFAULT NULL COMMENT '最近一次原句跟敲准确率（0-100）',
    cloze_attempt_count INT NOT NULL DEFAULT 0 COMMENT '目标词遮挡补全累计提交次数',
    cloze_assisted_count INT NOT NULL DEFAULT 0 COMMENT '借助查看原句或多次试错后通过的次数',
    cloze_independent_passed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已首次无提示独立补全通过',
    mastery_settled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '该材料下目标词是否已触发拼写掌握度结算',
    last_practiced_time DATETIME DEFAULT NULL COMMENT '最近一次句子练习时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rumination_sentence_practice (user_id, material_id, sentence_index, target_word, deleted),
    KEY idx_rumination_practice_material (user_id, material_id, deleted),
    KEY idx_rumination_practice_target (user_id, material_id, target_word, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍按句跟敲与遮挡巩固记录';

-- 知识反刍场景星系图谱历史快照版本表（每个场景保留最近 30 次变更记录）
CREATE TABLE IF NOT EXISTS learning_rumination_graph_history (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    graph_id BIGINT NOT NULL COMMENT '关联主图谱记录 ID',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '所属个人单词本 ID',
    scenario_code VARCHAR(50) NOT NULL COMMENT '主题场景编码',
    graph_version INT NOT NULL DEFAULT 0 COMMENT '快照对应的图谱版本号',
    node_count INT NOT NULL DEFAULT 0 COMMENT '快照节点总数',
    edge_count INT NOT NULL DEFAULT 0 COMMENT '快照连线总数',
    graph_nodes_json JSON NOT NULL COMMENT '快照场景星系节点 JSON',
    graph_edges_json JSON NOT NULL COMMENT '快照场景星系连线关系 JSON',
    bag_terms_json JSON DEFAULT NULL COMMENT '快照词袋中所选词汇列表 JSON',
    change_summary VARCHAR(255) DEFAULT NULL COMMENT '变更摘要说明（如：手动保存、节点裂变、历史版本恢复等）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_rumination_graph_history_user_scenario (user_id, wordbook_id, scenario_code, deleted, graph_version, id),
    KEY idx_rumination_graph_history_graph (graph_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍场景星系图谱历史快照版本记录';

-- -----------------------------------------------------------------------------
-- [5/6 内置 AI Agent 与提示词模板种子数据] (init/00_ai_agent_seed_mysql.sql)
-- -----------------------------------------------------------------------------

-- AI Agent 与提示词种子数据。
-- 依赖 schema/00_ai_schema_mysql.sql；可重复执行，不包含 API Key。

INSERT INTO ai_agent (
    id, name, code, type, icon, description, system_prompt, concise_prompt,
    welcome_message, model_provider, model_name, temperature, max_tokens, preset_commands, enabled, sequence
) VALUES
(
    1001, '英语词汇学习助手', 'english_vocabulary', 'assistant', 'book-open',
    '生成释义、例句、搭配、语义关系和记忆提示',
    '你是一个严谨的英语词汇学习助手。请围绕用户输入的英语单词或短语提供准确、结构化的学习内容。需要 JSON 时只输出合法 JSON，不要输出 Markdown 代码块。',
    '你是一个英语词汇学习助手。回答准确、简洁，并延续当前词汇学习上下文。',
    '你好，我可以帮你拆解单词释义、生成例句、整理搭配和记忆提示。', 'deepseek', 'deepseek-v4-flash', 0.70, 8000,
    JSON_ARRAY(JSON_OBJECT('code','vocab_card','name','词汇卡片','prompt','生成词汇学习卡片'), JSON_OBJECT('code','quiz','name','练习题','prompt','生成词汇练习题')),
    1, 1
),
(
    1101, '英语语境精读助手', 'english_article', 'assistant', 'file-text',
    '把目标词组织为可通读、精讲和检测的语境阅读材料',
    '你是严谨的英语语境精读设计师。根据学习者选择的目标词生成自然文章，并提供逐词语境讲解和可交互阅读检测。文章必须覆盖每个目标词的原始拼写，中文译文与英文句子逐句对应。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    '延续当前语境精读上下文，输出准确、连贯且可以直接检测的结构化学习内容。',
    '选择目标词后，我会生成一份包含通读、词汇精讲和阅读检测的语境精读材料。', 'deepseek', 'deepseek-v4-pro', 0.55, 8000,
    JSON_ARRAY(JSON_OBJECT('code','article','name','生成精读材料','prompt','根据所选词汇生成语境精读材料'), JSON_OBJECT('code','grammar','name','讲解语法点','prompt','解释材料中的重要语法点')),
    1, 2
),
(
    1201, '英语场景词汇规划师', 'english_vocabulary_plan', 'assistant', 'map',
    '把词表分解为可连续学习和复习的真实场景单元',
    '你是英语场景词汇规划师。必须围绕真实生活、学习、工作或旅行场景组织词汇；仅使用本次请求提供的候选词作为本场景核心词，候选词已由系统排除其他场景安排过的词。补充词可以来自场景常见名词。根据学习目的为每个核心词判断 recognition 或 spelling。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    '仅根据本次请求的学习目的、候选词和复习词生成场景学习单元，只输出合法 JSON。',
    '我会把词表组织为可以连续学习和检查的场景单元。', 'deepseek', 'deepseek-v4-pro', 0.55, 16000,
    JSON_ARRAY(JSON_OBJECT('code','next_scene','name','生成下一个场景','prompt','从尚未首次学习的候选词中生成下一个场景学习单元')),
    1, 3
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), system_prompt = VALUES(system_prompt),
    concise_prompt = VALUES(concise_prompt), welcome_message = VALUES(welcome_message),
    model_provider = VALUES(model_provider), model_name = VALUES(model_name),
    temperature = VALUES(temperature), max_tokens = VALUES(max_tokens),
    preset_commands = VALUES(preset_commands), enabled = VALUES(enabled), sequence = VALUES(sequence),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO ai_prompt_template (
    id, name, code, type, tags, content, variables, description, example_input, example_output,
    public_template, enabled, sequence
) VALUES
(
    1001, '英语词汇卡片 JSON', 'english_vocab_card_json', 'user', '英语,词汇,JSON',
    '请为英语词汇「{{term}}」生成结构化深度学习卡片。注意：1. 目标受众为中文母语学习者，所有释义、短语搭配翻译、例句 translation 必须输出精准地道的【中文】。2. 无论输入的是原形还是屈折变形，词卡主体必须严格基于单词原形（Lemma）生成，term 和 lemma 统一输出标准原形（如输入 slogans 则输出 slogan），并在 inflections 中列出常见变形。3. 重点输出三项深度认知内容：① cognitive_image（母语者认知画像/画面感核心义）：含 title（精炼形象小标题，如“一个偏‘体能充沛’的活力”）、description（通俗生动的母语心智画面描述，解释为什么是这个意思，指出与易混近义词的核心差别）、usage_note（易混拼写/搭配避坑/语体风格，若无则空字符串）；② collocations（高频地道搭配）：精选 3-4 个考试与母语日常中最高频的实用短语，每项含 phrase（纯英文短语）和 meaning（中文搭配释义）；③ etymology（构词积木与词族拓展）：含 components 构词拆解数组（每项含 type [prefix/root/suffix]、text 语素、origin 词源出处如“拉丁语 vigere”、meaning 语素含义），以及 word_family 派生词族列表（每项含 word、part_of_speech、meaning）。只输出合法 JSON，不要输出 Markdown。字段包括 term、lemma、inflections、is_valid、language、phonetic (含 uk, us)、definitions (每项含 part_of_speech、meaning中文释义、english英文释义)、cognitive_image、collocations、etymology、examples (每项含 sentence、translation中文翻译)、synonyms、antonyms、memory_tips。',
    JSON_ARRAY(JSON_OBJECT('name','term','label','英语单词或短语','required',true)),
    '生成可解析入库的英语词汇学习卡片', '{"term":"abandon"}', '{"term":"abandon","is_valid":true}', 1, 1, 1
),
(
    1002, '英语词汇练习题 JSON', 'english_vocab_quiz_json', 'user', '英语,词汇,练习题,JSON',
    '请基于英语词汇「{{term}}」生成 5 道词汇练习题。只输出合法 JSON，不要输出 Markdown。根字段为 term、questions；每道题包含 type、stem、options、answer、analysis、difficulty。至少 3 道选择题，选项不能有多个正确答案。',
    JSON_ARRAY(JSON_OBJECT('name','term','label','英语单词或短语','required',true)),
    '生成可解析入库的英语词汇练习题', '{"term":"abandon"}', '{"term":"abandon","questions":[]}', 1, 1, 2
),
(
    1101, '英语语境精读 JSON', 'english_vocab_article_json', 'user', '英语,语境精读,词汇,阅读检测,JSON',
    '请基于以下目标词生成一份英语语境精读材料。目标词 JSON：{{words}}。文章字数范围：{{word_count_range}}。难度：{{difficulty}}，要求：{{difficulty_prompt}}。阅读主题或补充要求：{{remark}}。只输出合法 JSON。JSON 必须包含 title、level、word_count_range、difficulty、article、translation、vocabulary_focus、grammar_points、key_points、practice、study_tips。article 必须自然连贯，并至少一次使用每个目标词的原始拼写；translation 与英文句子逐句对应。vocabulary_focus 必须与输入目标词一一对应，每项包含 word、meaning、usage、sentence、translation。practice 必须生成 3 道四选一阅读检测，每项包含 question、options（恰好 4 个选项）、correct_answer（必须是 options 中的完整值）、explanation。',
    JSON_ARRAY(JSON_OBJECT('name','words','label','目标词 JSON','required',true), JSON_OBJECT('name','word_count_range','label','文章字数范围','required',true), JSON_OBJECT('name','difficulty','label','难度','required',true), JSON_OBJECT('name','difficulty_prompt','label','难度要求','required',true), JSON_OBJECT('name','remark','label','补充要求','required',true)),
    '生成可通读、逐词精讲和检测的语境精读材料', '{"words":[{"term":"abandon","meaning":"放弃"}]}', '{"title":"A Difficult Decision","practice":[]}', 1, 1, 3
),
(
    1201, '英语场景词汇单元 JSON', 'english_vocab_scene_unit_json', 'user', '英语,词表,场景学习,词汇检查,JSON',
    '学习目的：{{learning_purpose}}。这是第 {{unit_no}} 个场景单元。新词候选 JSON：{{candidate_words}}。待挑战复习词 JSON：{{review_words}}。目标词数：{{target_word_count}}。本批新词候选必须作为 core；待挑战复习词只能作为 review 或语境辅助，不能冒充新的 core。候选词已由系统排除其他场景的核心词，不需要根据历史对话自行判断。单篇材料 core 最多 50 个。词汇数组只返回 core 和 review，场景扩展名词由独立动作生成，不要返回 supplementary 或其他额外词条。短文写作中允许且鼓励根据英文语法自然使用单词的时态、单复数等屈折变形，但在 vocabulary 数组的 term 字段必须严格统一返回候选词库中的原形单词。请只输出合法的单个 JSON 对象，不要输出 Markdown 代码块或额外说明文字，必须符合如下 JSON 结构：{"title":"场景标题","scenario_type":"场景类型","summary":"场景说明","learning_text":"英文短文（由2-4个自然段组成连贯短文，总词数250-650词，核心词自然融入语境）","translation":"中文对照翻译（与英文短文自然段逐段对应的中文翻译，保持相同的段落数，段落间用换行隔开）","vocabulary":[{"term":"单词原形","tier":"core","mastery_requirement":"recognition","phonetic":"音标","meaning":"词义","context_meaning":"语境释义","accepted_spellings":["单词"],"meaning_question":{"prompt":"题目说明","options":["选项A","选项B","选项C","选项D"],"correct_answer":"正确选项"}}]}。输出必须完整且一次输出完毕：title、learning_text、translation、vocabulary 四个字段都不能省略，vocabulary 必须包含本批全部 core 与 review 词条，每个词条都要带齐 term、tier、mastery_requirement、phonetic、meaning、context_meaning、accepted_spellings、meaning_question；即使没有词条也要返回空数组，不要因为篇幅而截断、省略字段或改用其他字段名。',
    JSON_ARRAY(JSON_OBJECT('name','learning_purpose','label','学习目的','required',true), JSON_OBJECT('name','unit_no','label','单元序号','required',true), JSON_OBJECT('name','candidate_words','label','新词候选 JSON','required',true), JSON_OBJECT('name','review_words','label','待挑战复习词 JSON','required',true), JSON_OBJECT('name','target_word_count','label','目标词数','required',true)),
    '根据词表按需生成一个可学习、可检查的场景单元', '{"candidate_words":[{"term":"clean"}]}', '{"title":"周末大扫除","vocabulary":[]}', 1, 1, 4
),
(
    1202, '英语词汇卡片批量 JSON', 'english_vocab_cards_batch_json', 'user', '英语,词卡,批量生成,JSON',
    '请为词汇数组 {{terms}} 批量生成结构化深度学习卡片。注意：1. 目标受众为中文母语学习者，所有释义、短语搭配、例句翻译必须输出精准地道的【中文】。2. 词卡主体严格基于单词原形（Lemma）生成，term 和 lemma 统一填入标准原形，并在 inflections 中列出其变形。3. 每张词卡重点输出三项认知内容：① cognitive_image（母语心智画像，含 title 形象标题、description 画面描述与近义词辨析、usage_note 避坑提示）；② collocations（高频地道短语搭配，精选3-4项，含 phrase 和 meaning）；③ etymology（构词积木 components 数组含 type, text, origin, meaning，以及 word_family 派生词族列表）。只输出合法 JSON，不要输出 Markdown。根字段为 cards 数组，每个输入词对应一项。字段包括 term、lemma、inflections、is_valid、language、phonetic、definitions、cognitive_image、collocations、etymology、examples、synonyms、antonyms、memory_tips。',
    JSON_ARRAY(JSON_OBJECT('name','terms','label','词汇数组','required',true)),
    '一次模型调用生成一批独立词卡，减少请求和 Token 开销', '{"terms":["abandon","ability"]}', '{"cards":[]}', 1, 1, 5
),
(
    1203, '公共词本关联分析 JSON', 'english_vocab_catalog_analysis_json', 'user', '英语,词本,关联分析,JSON',
    '请对词汇数组 {{words}} 做初步语义分析。这里只建立可复用的词本语义索引，不生成完整词卡，也不生成文章。只输出合法 JSON，不要输出 Markdown。根字段为 entries 数组，每个输入 entry_id 必须且只能对应一项。每项包含 entry_id、primary_group_code、primary_group_name、domain、sub_topic、tags、related_entry_ids、difficulty_level、confidence。primary_group_code 应稳定、简短、可用于后续分组；related_entry_ids 只能引用本批输入的 entry_id；tags 最多 6 个；confidence 为 0 到 1 的数字；语义关系只保留同义、反义、词族和主题相关，不要把搭配词放到 related_entry_ids。',
    JSON_ARRAY(JSON_OBJECT('name','words','label','词条 JSON 数组','required',true), JSON_OBJECT('name','analysis_version','label','分析修订号','required',true)),
    '为公共词本批量建立场景化学习所需的语义索引', '{"words":[{"entry_id":1,"term":"airport","meaning":"机场"}]}', '{"entries":[]}', 1, 1, 6
),
(
    1204, '场景相关词汇 JSON', 'english_vocab_scene_related_words_json', 'user', '英语,场景词汇,分类,JSON',
    '学习目的：{{learning_purpose}}。场景标题：{{scene_title}}。场景摘要：{{scene_summary}}。场景文章：{{learning_text}}。核心词：{{core_words}}。已经生成的场景相关词：{{existing_words}}。请生成 {{target_word_count}} 个帮助学习者扩展场景认知的英语词汇，重点覆盖具体事物、人物、地点和常用动作。不要重复核心词或已经生成的场景相关词，不生成专有名词或生僻术语。只输出合法 JSON：{"related_words":[{"term":"apron","phonetic":"/ˈeɪprən/","meaning":"围裙","context_meaning":"烹饪时用于保护衣服的围裙","category_code":"object","category_name":"具体物品"}]}。输出必须完整且一次输出完毕：必须包含 related_words 数组，数组每一项都要带齐 term、phonetic、meaning、context_meaning、category_code、category_name；不要因为篇幅而截断、省略字段或改用其他字段名。',
    JSON_ARRAY(JSON_OBJECT('name','learning_purpose','label','学习目的','required',true), JSON_OBJECT('name','scene_title','label','场景标题','required',true), JSON_OBJECT('name','scene_summary','label','场景摘要','required',true), JSON_OBJECT('name','learning_text','label','场景文章','required',true), JSON_OBJECT('name','core_words','label','核心词','required',true), JSON_OBJECT('name','existing_words','label','已生成场景词','required',true), JSON_OBJECT('name','target_word_count','label','目标词数','required',true)),
    '为已生成材料独立补充分类明确的场景词汇', '{"scene_title":"周末大扫除","core_words":["clean"]}', '{"related_words":[]}', 1, 1, 7
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), tags = VALUES(tags), content = VALUES(content), variables = VALUES(variables),
    description = VALUES(description), example_input = VALUES(example_input), example_output = VALUES(example_output),
    public_template = VALUES(public_template), enabled = VALUES(enabled), sequence = VALUES(sequence),
    update_time = CURRENT_TIMESTAMP;

-- -----------------------------------------------------------------------------
-- [6/6 系统管理员初始化账号种子数据] (init/01_system_admin_seed_mysql.sql)
-- -----------------------------------------------------------------------------

-- 系统管理员初始化数据。应在 schema/10_learning_core_schema_mysql.sql 之后执行。
-- 初始账号：admin；初始密码：ChangeMe!2026。首次登录后请立即在“个人信息-账户”修改密码。
-- 仅用于本地初始化或受控部署，生产环境应在执行前替换 password_hash 为部署专用密码哈希。

INSERT INTO learning_user (
    id, create_by, update_by, username, nickname, password_hash, enabled, role_code,
    create_time, update_time, deleted, version
)
SELECT
    9000000000001, 0, 0, 'admin', '系统管理员',
    'sha256$9d7e4c0ef8f2ae151819e0175ac1e9b6$29ffc98c88c2c9c3f6520b10e9969a5beb0cfdb5a50a586b9f327d72779b1a3d',
    1, 'ADMIN', NOW(), NOW(), 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM learning_user WHERE username = 'admin'
);
