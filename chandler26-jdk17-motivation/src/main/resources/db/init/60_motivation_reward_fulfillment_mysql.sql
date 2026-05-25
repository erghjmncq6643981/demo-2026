-- 奖励实现方式与礼物兑换券状态增量脚本。
-- 如果已执行过 20_motivation_point_reward_init_mysql.sql，请执行本脚本补齐 1.0 字段。

ALTER TABLE motivation_reward
    ADD COLUMN fulfillment_type VARCHAR(32) NOT NULL DEFAULT 'INVENTORY_DEDUCT'
        COMMENT '奖励实现方式：INVENTORY_DEDUCT、PARENT_EXECUTE、PARENT_PURCHASE、PARENT_FULFILL'
        AFTER exchange_limit_count;

ALTER TABLE motivation_reward_exchange
    ADD COLUMN fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT '礼物实现状态：PENDING、SCHEDULED、IN_PROGRESS、COMPLETED、CONFIRMED'
        AFTER status,
    ADD COLUMN fulfillment_updated_by_user_id BIGINT DEFAULT NULL
        COMMENT '礼物状态更新人用户 ID'
        AFTER reviewed_by_user_id,
    ADD COLUMN fulfillment_updated_at DATETIME DEFAULT NULL
        COMMENT '礼物状态更新时间'
        AFTER reviewed_at,
    ADD COLUMN confirmed_by_user_id BIGINT DEFAULT NULL
        COMMENT '礼物确认人用户 ID'
        AFTER completed_at,
    ADD COLUMN confirmed_at DATETIME DEFAULT NULL
        COMMENT '礼物确认时间'
        AFTER confirmed_by_user_id,
    ADD KEY idx_motivation_reward_exchange_fulfillment (child_id, fulfillment_status, update_time);
