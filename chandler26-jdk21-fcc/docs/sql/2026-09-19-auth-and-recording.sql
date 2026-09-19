-- -----------------------------------------------------------------------------
-- FCC 账号体系改造迁移脚本
-- 目标数据库: MySQL 8.0+
-- 说明: 项目当前未引入 Flyway/Liquibase，本文件为按序手工执行的一次性迁移。
--       若目标库已由 docs/fcc-schema.sql 全量初始化，第 1、2 节可跳过。
-- -----------------------------------------------------------------------------

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -----------------------------------------------------------------------------
-- 1. 坐席表补齐登录口令字段 (坐席账号以 fcc_agent 为唯一基准)
-- -----------------------------------------------------------------------------

ALTER TABLE fcc_agent
    ADD COLUMN password_hash       VARCHAR(255) NULL COMMENT '坐席登录口令派生串 pbkdf2-sha256$iterations$salt$hash，为空表示禁止登录' AFTER status,
    ADD COLUMN password_updated_at DATETIME(3) NULL COMMENT '口令最近一次设置时间' AFTER password_hash,
    ADD COLUMN last_login_at       DATETIME(3) NULL COMMENT '最近一次成功登录时间' AFTER password_updated_at;

-- -----------------------------------------------------------------------------
-- 2. 新建管理控制台账号表 (系统超管与运营人员以本表为唯一基准)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fcc_admin_user (
    id                    BIGINT UNSIGNED NOT NULL,
    tenant_id             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    username              VARCHAR(64) NOT NULL COMMENT '管理控制台登录账号',
    real_name             VARCHAR(128) NOT NULL COMMENT '账号显示姓名',
    password_hash         VARCHAR(255) NOT NULL COMMENT '口令派生串 pbkdf2-sha256$iterations$salt$hash',
    password_updated_at   DATETIME(3) NULL COMMENT '口令最近一次设置时间',
    role_code             VARCHAR(64) NOT NULL DEFAULT 'ADMIN' COMMENT '控制台角色: ADMIN / OPERATOR / AUDITOR',
    status                VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
    last_login_at         DATETIME(3) NULL COMMENT '最近一次成功登录时间',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at            DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username),
    KEY idx_admin_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Management console account';

-- -----------------------------------------------------------------------------
-- 3. 录音元数据表补齐节点字段 (记录写出录音文件的 Sidecar 节点)
-- -----------------------------------------------------------------------------

ALTER TABLE fcc_call_recording
    ADD COLUMN node_id VARCHAR(128) NULL COMMENT '写出该录音文件的 Sidecar/FreeSWITCH 节点' AFTER bridge_id;

-- -----------------------------------------------------------------------------
-- 4. 初始化系统超级管理员
--
-- 账号: admin
-- 初始口令: Fcc@2026#Init
-- 【安全提示】该口令在本文档中公开，仅用于首次引导。请登录后第一时间通过
--            POST /api/admin/auth/change-password 修改，或直接置空本行记录。
--
-- 口令为 PBKDF2-HMAC-SHA256 派生 (迭代 120000 / 16 字节盐 / 32 字节密钥)，
-- 存储格式 pbkdf2-sha256$迭代次数$盐Hex$哈希Hex，可由应用重新生成：
--   PasswordHasher.hash("新的明文口令")
-- -----------------------------------------------------------------------------

INSERT INTO fcc_admin_user (id, tenant_id, username, real_name, password_hash, password_updated_at, role_code, status)
VALUES (
    1,
    0,
    'admin',
    '系统超级管理员',
    'pbkdf2-sha256$120000$2d80e5e0f7279a9fabf9704eaedde3d4$a80b0d7f310b92b961d5d5a6077186bdf72b6c7a30ad0d8a3ec3fa39bcf9d201',
    CURRENT_TIMESTAMP(3),
    'ADMIN',
    'ENABLED'
)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), role_code = VALUES(role_code);
