-- 在停写备份后先执行，再部署依赖此表的 server；无外键，不修改通话事实。
ALTER TABLE fcc_event_inbox
    ADD COLUMN recovery_attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN recovery_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN recovery_actor VARCHAR(64) NULL;
CREATE TABLE IF NOT EXISTS fcc_event_recovery_action (
    action_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    event_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_error VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    KEY idx_event_recovery_action (event_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE fcc_call_session ADD KEY idx_call_started (started_at);
-- 执行前检查重复 leg_id：若有重复需人工确认归属，不自动删除历史。
-- SELECT leg_id,COUNT(*) FROM fcc_agent_service_session WHERE leg_id IS NOT NULL GROUP BY leg_id HAVING COUNT(*)>1;
ALTER TABLE fcc_agent_service_session
    ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_agent_service_leg (leg_id);
CREATE TABLE IF NOT EXISTS fcc_call_recovery_issue (
    call_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error VARCHAR(128) NULL,
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (call_id),
    KEY idx_recovery_issue_status (status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Isolated call restoration failures';
