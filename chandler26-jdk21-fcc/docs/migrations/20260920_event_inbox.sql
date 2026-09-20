-- Add before enabling the durable consumer; no rewrites to call facts.
CREATE TABLE IF NOT EXISTS fcc_event_inbox (
 event_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 node_id VARCHAR(128) NOT NULL,
 payload JSON NOT NULL,
 status VARCHAR(16) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 KEY idx_inbox_status_time(status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Retain UNKNOWN/FAILED payloads for reconciliation. Never blindly reset them to PROCESSING.
