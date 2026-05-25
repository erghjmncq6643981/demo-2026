-- 激励系统演示账号补充脚本。
-- 已执行过 00/10/20/30 初始化脚本的环境，可以单独执行本文件。
-- 演示账号：
--   家长：demo-parent / 123456
--   孩子：demo-child / 123456

INSERT INTO motivation_user (
    id,
    username,
    nickname,
    password_hash,
    user_type,
    enabled,
    deleted
) VALUES (
    202605250002,
    'demo-child',
    '小星',
    'sha256$6368696c642d64656d6f2d32303236$43003bf4171a4b22deac076934ec4828f4c8e52118e8f2953b65ea692aa7d2bf',
    'CHILD',
    1,
    0
)
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    password_hash = VALUES(password_hash),
    user_type = 'CHILD',
    enabled = 1,
    deleted = 0;

INSERT INTO motivation_user (
    id,
    username,
    nickname,
    password_hash,
    user_type,
    enabled,
    deleted
)
SELECT
    202605250001,
    'demo-parent',
    '星星家长',
    'sha256$706172656e742d64656d6f2d32303236$8bf31f00566b4d45c64f97618146721fcf378dcf25b19b81871c3c3701f56d41',
    'PARENT',
    1,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM motivation_user WHERE username = 'demo-parent'
);

UPDATE motivation_user
SET nickname = '星星家长',
    password_hash = 'sha256$706172656e742d64656d6f2d32303236$8bf31f00566b4d45c64f97618146721fcf378dcf25b19b81871c3c3701f56d41',
    user_type = 'PARENT',
    enabled = 1,
    deleted = 0
WHERE username = 'demo-parent';

SET @demo_parent_user_id = (
    SELECT id FROM motivation_user WHERE username = 'demo-parent' LIMIT 1
);
SET @demo_child_user_id = (
    SELECT id FROM motivation_user WHERE username = 'demo-child' LIMIT 1
);
SET @demo_child_id = (
    SELECT fm.child_id
    FROM motivation_family_member fm
    JOIN motivation_child c ON c.id = fm.child_id
    WHERE fm.user_id = @demo_parent_user_id
      AND fm.status = 'ACTIVE'
      AND c.deleted = 0
    ORDER BY fm.is_primary DESC, c.update_time DESC
    LIMIT 1
);

INSERT INTO motivation_child (
    id,
    nickname,
    gender,
    remark,
    status,
    deleted,
    created_by_user_id
)
SELECT
    202605250101,
    '小星',
    'UNKNOWN',
    '1.0 默认孩子档案',
    'ACTIVE',
    0,
    @demo_parent_user_id
WHERE @demo_child_id IS NULL;

SET @demo_child_id = COALESCE(@demo_child_id, 202605250101);

INSERT INTO motivation_family_member (
    id,
    child_id,
    user_id,
    relation_role,
    is_primary,
    can_manage,
    status
)
SELECT
    202605250201,
    @demo_child_id,
    @demo_parent_user_id,
    'PARENT',
    1,
    1,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM motivation_family_member
    WHERE child_id = @demo_child_id
      AND user_id = @demo_parent_user_id
);

UPDATE motivation_family_member
SET relation_role = 'PARENT',
    is_primary = 1,
    can_manage = 1,
    status = 'ACTIVE'
WHERE child_id = @demo_child_id
  AND user_id = @demo_parent_user_id;

INSERT INTO motivation_family_member (
    id,
    child_id,
    user_id,
    relation_role,
    is_primary,
    can_manage,
    status
)
SELECT
    202605250202,
    @demo_child_id,
    @demo_child_user_id,
    'CHILD',
    0,
    0,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM motivation_family_member
    WHERE child_id = @demo_child_id
      AND user_id = @demo_child_user_id
);

UPDATE motivation_family_member
SET relation_role = 'CHILD',
    is_primary = 0,
    can_manage = 0,
    status = 'ACTIVE'
WHERE child_id = @demo_child_id
  AND user_id = @demo_child_user_id;
