-- 激励系统积分互换比例脚本。
-- 已执行过 10/20 初始化脚本的环境，可以单独执行本文件。

CREATE TABLE IF NOT EXISTS motivation_point_exchange_rule (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    star_weight INT NOT NULL DEFAULT 1 COMMENT '星星币币值，默认 1',
    flower_weight INT NOT NULL DEFAULT 10 COMMENT '红花币币值，默认 10',
    crown_weight INT NOT NULL DEFAULT 100 COMMENT '皇冠币币值，默认 100',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_point_exchange_rule_child (child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统积分币值';
