-- 学习助手用户、词书、标签、关联词与复习计划表。
-- 依赖 english_vocabulary_study_record_mysql.sql，请在词汇缓存表之后执行。

CREATE TABLE IF NOT EXISTS learning_user (
    id BIGINT NOT NULL COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最近登录时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_user_username (username),
    KEY idx_learning_user_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习助手用户';

CREATE TABLE IF NOT EXISTS learning_user_token (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    token_hash VARCHAR(128) NOT NULL COMMENT '访问令牌 SHA-256 哈希',
    expired_time DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已注销',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_user_token_hash (token_hash),
    KEY idx_learning_user_token_user (user_id),
    KEY idx_learning_user_token_expired_time (expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习助手登录令牌';

CREATE TABLE IF NOT EXISTS learning_wordbook (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    name VARCHAR(64) NOT NULL COMMENT '词书名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '词书描述',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认词书',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_learning_wordbook_user (user_id, deleted, update_time),
    KEY idx_learning_wordbook_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习词书';

CREATE TABLE IF NOT EXISTS learning_wordbook_entry (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '词书 ID',
    vocabulary_id BIGINT NOT NULL COMMENT '词汇缓存 ID',
    term VARCHAR(128) NOT NULL COMMENT '展示单词或短语',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    note VARCHAR(500) DEFAULT NULL COMMENT '用户备注',
    review_stage INT NOT NULL DEFAULT 0 COMMENT '艾宾浩斯复习阶段',
    mastery_score INT NOT NULL DEFAULT 0 COMMENT '掌握度 0-100',
    first_review_time DATETIME DEFAULT NULL COMMENT '首次复习时间',
    last_review_time DATETIME DEFAULT NULL COMMENT '最近复习时间',
    next_review_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次复习时间',
    due_count INT NOT NULL DEFAULT 0 COMMENT '进入复习队列次数',
    review_count INT NOT NULL DEFAULT 0 COMMENT '复习次数',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '记住次数',
    wrong_count INT NOT NULL DEFAULT 0 COMMENT '忘记次数',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_wordbook_entry_term (wordbook_id, normalized_term),
    KEY idx_learning_wordbook_entry_user_due (user_id, deleted, next_review_time),
    KEY idx_learning_wordbook_entry_vocabulary (vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词书词条与复习状态';

CREATE TABLE IF NOT EXISTS learning_vocabulary_tag (
    id BIGINT NOT NULL COMMENT '主键',
    vocabulary_id BIGINT NOT NULL COMMENT '词汇缓存 ID',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    tag_type VARCHAR(50) NOT NULL COMMENT '标签类型：part_of_speech、meaning_topic、difficulty、collocation、word_family',
    tag_value VARCHAR(128) NOT NULL COMMENT '标签值',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    weight INT NOT NULL DEFAULT 50 COMMENT '标签权重 0-100',
    source VARCHAR(50) NOT NULL DEFAULT 'parsed_json' COMMENT '来源',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_vocabulary_tag (vocabulary_id, tag_type, tag_value),
    KEY idx_learning_vocabulary_tag_lookup (normalized_term, tag_type),
    KEY idx_learning_vocabulary_tag_value (tag_type, tag_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词汇多维标签';

CREATE TABLE IF NOT EXISTS learning_vocabulary_relation (
    id BIGINT NOT NULL COMMENT '主键',
    vocabulary_id BIGINT NOT NULL COMMENT '词汇缓存 ID',
    related_vocabulary_id BIGINT DEFAULT NULL COMMENT '已入库关联词汇 ID',
    normalized_term VARCHAR(128) NOT NULL COMMENT '当前词归一化值',
    related_term VARCHAR(128) NOT NULL COMMENT '关联单词、短语或搭配',
    relation_type VARCHAR(50) NOT NULL COMMENT '关联类型：synonym、antonym、word_family、collocation、tag_overlap',
    relation_value VARCHAR(128) DEFAULT NULL COMMENT '关联说明或共享标签',
    related_part_of_speech VARCHAR(50) DEFAULT NULL COMMENT '关联词核心词性',
    related_meaning VARCHAR(512) DEFAULT NULL COMMENT '关联词或搭配核心含义',
    match_type VARCHAR(50) DEFAULT NULL COMMENT '匹配来源：parsed_object、parsed_text、cached_exact、fuzzy',
    match_score INT DEFAULT NULL COMMENT '匹配分数 0-100',
    score INT NOT NULL DEFAULT 50 COMMENT '相关度 0-100',
    source VARCHAR(50) NOT NULL DEFAULT 'parsed_json' COMMENT '来源',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_vocabulary_relation (normalized_term, related_term, relation_type),
    KEY idx_learning_vocabulary_relation_term (normalized_term, score),
    KEY idx_learning_vocabulary_relation_related (related_term),
    KEY idx_learning_vocabulary_relation_match (normalized_term, match_type, match_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='词汇关联关系';

CREATE TABLE IF NOT EXISTS learning_review_record (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '词书 ID',
    entry_id BIGINT NOT NULL COMMENT '词书词条 ID',
    vocabulary_id BIGINT NOT NULL COMMENT '词汇缓存 ID',
    normalized_term VARCHAR(128) NOT NULL COMMENT '归一化单词或短语',
    result VARCHAR(20) NOT NULL COMMENT '复习结果：remembered、vague、forgotten',
    score INT DEFAULT NULL COMMENT '本次自评分',
    review_stage_before INT NOT NULL COMMENT '复习前阶段',
    review_stage_after INT NOT NULL COMMENT '复习后阶段',
    mastery_before INT NOT NULL COMMENT '复习前掌握度',
    mastery_after INT NOT NULL COMMENT '复习后掌握度',
    next_review_time DATETIME NOT NULL COMMENT '下次复习时间',
    duration_seconds INT DEFAULT NULL COMMENT '本次耗时秒数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_learning_review_record_user_time (user_id, create_time),
    KEY idx_learning_review_record_entry (entry_id, create_time),
    KEY idx_learning_review_record_term (normalized_term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复习记录';
