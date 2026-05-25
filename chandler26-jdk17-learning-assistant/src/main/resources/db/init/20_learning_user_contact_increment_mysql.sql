-- 学习用户联系方式增量脚本。
-- 若已执行 10_learning_core_init_mysql.sql，可单独执行本文件补充账户安全设置字段。

ALTER TABLE learning_user
    ADD COLUMN phone VARCHAR(32) DEFAULT NULL COMMENT '手机号码' AFTER nickname,
    ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '联系邮箱' AFTER phone;
