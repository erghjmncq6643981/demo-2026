-- 系统管理员初始化数据。应在 40_user_authorization_schema_mysql.sql 之后执行。
-- 初始账号：admin；初始密码：ChangeMe!2026。首次登录后请立即在“个人信息-账户”修改密码。
-- 仅用于本地初始化或受控部署，生产环境应在执行前替换 password_hash 为部署专用密码哈希。

INSERT INTO learning_user (
    id, create_by, update_by, username, nickname, password_hash, enabled, role_code,
    create_time, update_time, deleted, version
)
SELECT
    9000000000001, 0, 0, 'admin', '系统管理员',
    'sha256$9d7e4c0ef8f2ae151819e0175ac1e9b6$29ffc98c88c2c9c3f6520b10e9969a5beb0cfdb5a50a586b9f327d72779b1a3d',
    1, 'ADMIN', NOW(), NOW(), 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM learning_user WHERE username = 'admin'
);
