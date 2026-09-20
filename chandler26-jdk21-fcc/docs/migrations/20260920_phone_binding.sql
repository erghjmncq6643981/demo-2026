-- Apply before server; no existing rows are rewritten. Keep tables if rolling back application.
CREATE TABLE IF NOT EXISTS fcc_phone_binding_lock (
 tenant_id BIGINT UNSIGNED NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS fcc_phone_binding_challenge (
 tenant_id BIGINT UNSIGNED NOT NULL,
 owner_work_no VARCHAR(64) NOT NULL,
 extension VARCHAR(32) NOT NULL,
 code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 channel_uuid VARCHAR(64) NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,owner_work_no),
 UNIQUE KEY uk_binding_code(code_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
