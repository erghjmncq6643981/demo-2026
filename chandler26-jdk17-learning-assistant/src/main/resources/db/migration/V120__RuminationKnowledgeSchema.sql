-- 知识反刍场景星系图谱拓扑、材料文章与微环境探针表结构

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
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_rumination_material_user_wb_scenario (user_id, wordbook_id, scenario_code, deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识反刍场景材料文章';

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
