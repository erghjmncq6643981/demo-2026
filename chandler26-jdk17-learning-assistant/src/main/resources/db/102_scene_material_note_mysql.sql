-- 场景材料学习笔记：按用户和场景单元保存 Markdown 内容。
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
    UNIQUE KEY uk_learning_scene_material_note_user_unit (user_id, unit_id, deleted),
    KEY idx_learning_scene_material_note_material (scene_material_id, deleted),
    KEY idx_learning_scene_material_note_plan (plan_id, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习者场景材料 Markdown 笔记';
