-- Forward-only additive migration. No business data backfill or destructive rollback.
-- Keep receipts if rolling back application code; archive closed/expired payloads under the privacy policy.
CREATE TABLE IF NOT EXISTS fcc_screen_pop_delivery (
 tenant_id BIGINT UNSIGNED NOT NULL,
 owner_work_no VARCHAR(64) NOT NULL,
 call_id BIGINT UNSIGNED NOT NULL,
 payload JSON NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 received_at DATETIME(3) NULL,
 shown_at DATETIME(3) NULL,
 activated_at DATETIME(3) NULL,
 unsupported_at DATETIME(3) NULL,
 closed_at DATETIME(3) NULL,
 PRIMARY KEY(tenant_id,owner_work_no,call_id),
 KEY idx_pop_owner_expiry(tenant_id,owner_work_no,closed_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
