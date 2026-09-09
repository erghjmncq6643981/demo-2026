-- 学习活动原始事件与日汇总读模型。事件只追加，日汇总由后台投影器批量维护。
CREATE TABLE IF NOT EXISTS learning_activity_event (
    id BIGINT NOT NULL COMMENT '活动事件主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '活动所属用户 ID',
    event_type VARCHAR(40) NOT NULL COMMENT '活动类型编码',
    occurred_at DATETIME NOT NULL COMMENT '行为实际发生时间',
    plan_id BIGINT DEFAULT NULL COMMENT '关联学习计划 ID',
    unit_id BIGINT DEFAULT NULL COMMENT '关联场景单元 ID',
    material_id BIGINT DEFAULT NULL COMMENT '关联场景材料或文章记录 ID',
    entry_id BIGINT DEFAULT NULL COMMENT '关联个人单词本词条 ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '本次行为涉及数量',
    duration_seconds INT DEFAULT NULL COMMENT '有效学习时长，单位秒',
    result_code VARCHAR(30) DEFAULT NULL COMMENT '行为结果编码',
    idempotency_key VARCHAR(180) NOT NULL COMMENT '业务幂等键',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '投影状态：pending、processing、succeeded',
    claim_token VARCHAR(64) DEFAULT NULL COMMENT '批量投影领取令牌',
    processed_time DATETIME DEFAULT NULL COMMENT '投影成功时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_activity_event_idempotency (user_id, idempotency_key),
    KEY idx_learning_activity_event_pending (status, deleted, create_time),
    KEY idx_learning_activity_event_user_time (user_id, deleted, occurred_at),
    KEY idx_learning_activity_event_business (user_id, event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户学习活动原始事件';

CREATE TABLE IF NOT EXISTS learning_activity_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动日汇总主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    activity_date DATE NOT NULL COMMENT '活动日期',
    metric_type VARCHAR(40) NOT NULL COMMENT '日汇总指标编码',
    metric_count INT NOT NULL DEFAULT 0 COMMENT '指标数量',
    duration_seconds INT NOT NULL DEFAULT 0 COMMENT '有效时长，单位秒',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '正确数量',
    total_count INT NOT NULL DEFAULT 0 COMMENT '尝试总数量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_activity_daily_user_date_metric (user_id, activity_date, metric_type),
    KEY idx_learning_activity_daily_user_date (user_id, deleted, activity_date),
    KEY idx_learning_activity_daily_metric_date (metric_type, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户学习活动日汇总读模型';

-- 将历史词条加入和复习记录回填为可追溯的日指标。无法从历史数据推断真正的学习完成行为。
INSERT INTO learning_activity_daily
    (user_id, activity_date, metric_type, metric_count, duration_seconds, correct_count, total_count,
     create_by, create_time, update_by, update_time, deleted, version)
SELECT user_id, DATE(create_time), 'word_added', COUNT(*), 0, 0, 0,
       MIN(create_by), MIN(create_time), MIN(update_by), MAX(update_time), 0, 0
FROM learning_wordbook_entry
WHERE deleted = 0
GROUP BY user_id, DATE(create_time)
ON DUPLICATE KEY UPDATE metric_count = VALUES(metric_count), update_time = VALUES(update_time);

INSERT INTO learning_activity_daily
    (user_id, activity_date, metric_type, metric_count, duration_seconds, correct_count, total_count,
     create_by, create_time, update_by, update_time, deleted, version)
SELECT user_id, DATE(create_time), 'word_reviewed', COUNT(*),
       COALESCE(SUM(duration_seconds), 0),
       SUM(CASE WHEN result IN ('remembered', 'correct') THEN 1 ELSE 0 END),
       COUNT(*), MIN(create_by), MIN(create_time), MIN(update_by), MAX(update_time), 0, 0
FROM learning_review_record
WHERE deleted = 0
GROUP BY user_id, DATE(create_time)
ON DUPLICATE KEY UPDATE
    metric_count = VALUES(metric_count),
    duration_seconds = VALUES(duration_seconds),
    correct_count = VALUES(correct_count),
    total_count = VALUES(total_count),
    update_time = VALUES(update_time);
