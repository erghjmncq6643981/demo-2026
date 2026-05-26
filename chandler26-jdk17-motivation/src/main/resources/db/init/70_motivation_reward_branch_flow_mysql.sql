-- 奖励主流程与分支履约流程增量脚本。
-- 已执行 60_motivation_reward_fulfillment_mysql.sql 的环境，请继续执行本脚本。

ALTER TABLE motivation_reward_exchange
    ADD COLUMN branch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT '奖励分支履约状态：PENDING、PURCHASE_ORDERED、PURCHASE_SHIPPING、PURCHASE_ARRIVED、SCHEDULED、IN_PROGRESS、COMPLETED'
        AFTER fulfillment_status,
    ADD COLUMN expected_arrival_date DATE DEFAULT NULL
        COMMENT '家长购买奖励的预期到达日期'
        AFTER fulfillment_updated_at,
    ADD COLUMN schedule_start_date DATE DEFAULT NULL
        COMMENT '家长实现奖励的日程开始日期'
        AFTER expected_arrival_date,
    ADD COLUMN schedule_end_date DATE DEFAULT NULL
        COMMENT '家长实现奖励的日程结束日期'
        AFTER schedule_start_date,
    ADD KEY idx_motivation_reward_exchange_branch_date (child_id, branch_status, expected_arrival_date, schedule_start_date);
