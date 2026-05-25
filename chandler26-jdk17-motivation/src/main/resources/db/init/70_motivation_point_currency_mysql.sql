-- 激励系统币值配置脚本。
-- 已执行过 50_motivation_point_exchange_rule_mysql.sql 的环境，可以单独执行本文件。

CREATE TABLE IF NOT EXISTS motivation_point_currency (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    point_type VARCHAR(20) NOT NULL COMMENT '积分类型：STAR、FLOWER、CROWN',
    name VARCHAR(32) NOT NULL COMMENT '币值名称，例如星星、红花、皇冠',
    icon VARCHAR(16) NOT NULL COMMENT '币值图标',
    color VARCHAR(32) DEFAULT NULL COMMENT '币值展示颜色',
    exchange_weight INT NOT NULL DEFAULT 1 COMMENT '币值比例，星星币默认 1、红花币默认 10、皇冠币默认 100',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、INACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0 否，1 是',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_point_currency_child_type (child_id, point_type, deleted),
    KEY idx_motivation_point_currency_child (child_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统币值配置';
