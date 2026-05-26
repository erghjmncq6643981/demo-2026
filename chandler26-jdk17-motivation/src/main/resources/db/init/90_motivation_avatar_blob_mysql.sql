-- 头像图片字段补丁脚本。
-- 已执行过 00/10 初始化脚本的环境，请单独执行本文件。

ALTER TABLE motivation_user
    ADD COLUMN avatar_data MEDIUMBLOB DEFAULT NULL COMMENT '压缩后的头像图片数据',
    ADD COLUMN avatar_content_type VARCHAR(64) DEFAULT NULL COMMENT '头像图片 MIME 类型';

ALTER TABLE motivation_child
    ADD COLUMN avatar_data MEDIUMBLOB DEFAULT NULL COMMENT '压缩后的头像图片数据',
    ADD COLUMN avatar_content_type VARCHAR(64) DEFAULT NULL COMMENT '头像图片 MIME 类型';
