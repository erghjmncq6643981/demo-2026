-- 宝贝子账户可查看密码密文字段补丁。
-- 已执行过 00/40/90 等初始化脚本的环境，可以单独执行本文件。

ALTER TABLE motivation_user
    ADD COLUMN password_cipher TEXT DEFAULT NULL COMMENT '密码可查看副本密文，仅用于家长查看/重置孩子子账户密码' AFTER password_hash;

