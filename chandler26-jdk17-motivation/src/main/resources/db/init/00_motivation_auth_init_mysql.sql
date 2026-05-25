-- 激励系统用户登录与令牌初始化脚本。
-- 适用于全新数据库初始化。

CREATE TABLE IF NOT EXISTS motivation_user (
    id BIGINT NOT NULL COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '显示昵称',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    user_type VARCHAR(20) NOT NULL DEFAULT 'PARENT' COMMENT '用户类型：PARENT、GUARDIAN、CHILD',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最近登录时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_username (username),
    KEY idx_motivation_user_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统用户';

CREATE TABLE IF NOT EXISTS motivation_user_token (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    token_hash VARCHAR(128) NOT NULL COMMENT '访问令牌 SHA-256 哈希',
    device_name VARCHAR(128) DEFAULT NULL COMMENT '设备名称',
    expired_time DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已注销',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_token_hash (token_hash),
    KEY idx_motivation_user_token_user (user_id),
    KEY idx_motivation_user_token_expired_time (expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统登录令牌';
