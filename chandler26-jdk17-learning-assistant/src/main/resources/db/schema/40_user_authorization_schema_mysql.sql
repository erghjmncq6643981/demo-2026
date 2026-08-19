-- 用户角色增量结构。应在 10_learning_core_schema_mysql.sql 之后执行一次。
-- 当前版本只有 USER 与 ADMIN 两个角色，避免为固定角色引入未使用的 RBAC 关联表。

ALTER TABLE learning_user
    ADD COLUMN role_code VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '系统角色：USER-普通用户，ADMIN-系统管理员' AFTER enabled,
    ADD KEY idx_learning_user_role_enabled (role_code, enabled, deleted);

-- 升级存量数据时，空角色统一按普通用户处理。
UPDATE learning_user
SET role_code = 'USER'
WHERE role_code IS NULL OR role_code = '';
