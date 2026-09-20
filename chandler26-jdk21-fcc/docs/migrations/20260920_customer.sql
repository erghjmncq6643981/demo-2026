-- Existing development databases: back up, then apply once before the new server.
-- No data rewrite or seeds. Rollback application first; retain this table for recovery.
CREATE TABLE IF NOT EXISTS fcc_customer (
 id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
 tenant_id BIGINT UNSIGNED NOT NULL,
 owner_work_no VARCHAR(64) NOT NULL,
 name VARCHAR(128) NOT NULL,
 phone_number VARCHAR(32) NOT NULL,
 company_name VARCHAR(255) NULL,
 notes TEXT NULL,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 KEY idx_customer_owner_page(tenant_id,owner_work_no,id),
 KEY idx_customer_owner_phone(tenant_id,owner_work_no,phone_number,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
