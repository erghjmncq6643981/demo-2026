-- 激励系统业务日志初始化脚本。
-- 适用于全新数据库初始化。

CREATE TABLE IF NOT EXISTS motivation_system_log (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT DEFAULT NULL COMMENT '操作用户 ID',
    child_id BIGINT DEFAULT NULL COMMENT '关联孩子 ID',
    log_type VARCHAR(50) NOT NULL COMMENT '日志类型：AUTH、TASK、POINT、REWARD、CALENDAR、SYSTEM',
    title VARCHAR(128) NOT NULL COMMENT '日志标题',
    detail TEXT DEFAULT NULL COMMENT '日志详情',
    source VARCHAR(50) DEFAULT NULL COMMENT '来源模块',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪 ID',
    request_ip VARCHAR(64) DEFAULT NULL COMMENT '请求 IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_system_log_user_time (user_id, create_time),
    KEY idx_motivation_system_log_child_time (child_id, create_time),
    KEY idx_motivation_system_log_type_time (log_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统业务日志';
