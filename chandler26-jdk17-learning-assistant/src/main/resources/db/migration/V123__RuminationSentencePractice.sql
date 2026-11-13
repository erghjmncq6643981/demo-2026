-- V123: 新增知识反刍场景材料按句跟敲与目标词遮挡补全巩固记录表，区分练习次数与独立掌握度结算。

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
