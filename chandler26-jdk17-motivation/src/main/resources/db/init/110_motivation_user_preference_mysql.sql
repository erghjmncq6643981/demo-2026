-- 用户偏好配置补丁。
-- 已执行过 00/10/20/30/40/50/60/70/80/90/100 初始化脚本的环境，请单独执行本文件。

CREATE TABLE IF NOT EXISTS motivation_user_preference (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    preference_key VARCHAR(64) NOT NULL COMMENT '配置项，例如 selectedChildId、taskCalendarViewMode',
    preference_value VARCHAR(512) DEFAULT NULL COMMENT '配置值',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_preference_user_key (user_id, preference_key),
    KEY idx_motivation_user_preference_user_time (user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统用户偏好配置';
