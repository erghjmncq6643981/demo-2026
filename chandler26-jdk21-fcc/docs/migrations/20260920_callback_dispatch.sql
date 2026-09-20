-- Apply once after dial_jobs. Existing records keep NULL; no historical dial is fabricated.
-- Rollback: retain this additive nullable column and linked job facts; do not drop live jobs.
ALTER TABLE fcc_callback_task ADD COLUMN last_dial_job_id BIGINT UNSIGNED NULL COMMENT '最近一次持久外呼任务';
CREATE INDEX idx_callback_job ON fcc_callback_task(tenant_id,last_dial_job_id);
