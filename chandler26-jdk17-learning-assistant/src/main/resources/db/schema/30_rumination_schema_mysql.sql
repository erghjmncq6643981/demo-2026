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
