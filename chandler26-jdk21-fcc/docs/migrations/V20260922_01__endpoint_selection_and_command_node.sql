-- Execution order:
-- 1. Stop writes to fcc_agent_endpoint_binding and fcc_call_command.
-- 2. Run the duplicate-detection queries below. Resolve every returned row.
-- 3. Apply this migration on MySQL 8.
-- 4. Deploy fcc-admin and fcc-server together, then deploy both frontends.

-- Preflight: every result must be resolved before the unique indexes are added.
SELECT agent_id, endpoint_type, endpoint_value, status, COUNT(*) AS duplicate_count
FROM fcc_agent_endpoint_binding
WHERE status = 'ENABLED'
GROUP BY agent_id, endpoint_type, endpoint_value, status
HAVING COUNT(*) > 1;

-- A SIP extension cannot be enabled for two different agents.
SELECT endpoint_value, COUNT(*) AS duplicate_sip_count
FROM fcc_agent_endpoint_binding
WHERE status = 'ENABLED'
  AND endpoint_type = 'SIP'
  AND endpoint_value IS NOT NULL
GROUP BY endpoint_value
HAVING COUNT(*) > 1;

-- A SIP binding without an extension cannot be routed and must be repaired first.
SELECT id, agent_id, endpoint_type, endpoint_value
FROM fcc_agent_endpoint_binding
WHERE status = 'ENABLED'
  AND endpoint_type = 'SIP'
  AND (endpoint_value IS NULL OR endpoint_value = '');

ALTER TABLE fcc_agent_endpoint_binding
    ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否为坐席当前接听终端' AFTER priority;

-- Backfill exactly one active binding per agent from the previous priority convention.
-- A temporary table avoids a target-table subquery during the MySQL UPDATE.
CREATE TEMPORARY TABLE fcc_endpoint_active_backfill (
    agent_id   BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    binding_id BIGINT UNSIGNED NOT NULL
) ENGINE=InnoDB;

INSERT INTO fcc_endpoint_active_backfill(agent_id, binding_id)
SELECT agent_id, binding_id
FROM (
    SELECT agent_id,
           id AS binding_id,
           ROW_NUMBER() OVER (PARTITION BY agent_id ORDER BY priority, id) AS row_no
    FROM fcc_agent_endpoint_binding
    WHERE status = 'ENABLED'
) ranked
WHERE row_no = 1;

UPDATE fcc_agent_endpoint_binding binding
JOIN fcc_endpoint_active_backfill active_binding
  ON active_binding.binding_id = binding.id
SET binding.is_active = 1;

DROP TEMPORARY TABLE fcc_endpoint_active_backfill;

ALTER TABLE fcc_agent_endpoint_binding
    ADD COLUMN active_agent_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN is_active = 1 AND status = 'ENABLED' THEN agent_id ELSE NULL END
        ) STORED AFTER is_active,
    ADD COLUMN enabled_binding_key VARCHAR(512)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ENABLED'
                 THEN CONCAT(agent_id, ':', endpoint_type, ':', COALESCE(endpoint_value, ''))
                 ELSE NULL END
        ) STORED AFTER status,
    ADD COLUMN enabled_sip_value VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ENABLED' AND endpoint_type = 'SIP' THEN endpoint_value ELSE NULL END
        ) STORED AFTER enabled_binding_key,
    ADD UNIQUE KEY uk_binding_active_agent (active_agent_id),
    ADD UNIQUE KEY uk_binding_enabled_identity (enabled_binding_key),
    ADD UNIQUE KEY uk_binding_enabled_sip (enabled_sip_value),
    ADD KEY idx_binding_endpoint_status (endpoint_type, endpoint_value, status);

ALTER TABLE fcc_call_command
    CHANGE COLUMN target_node_id assigned_node_id VARCHAR(128) NULL
        COMMENT 'Sidecar 接受命令后返回的实际执行节点；发送前未知';

CREATE TABLE fcc_agent_endpoint_selection_audit (
    id                  BIGINT UNSIGNED NOT NULL,
    agent_id            BIGINT UNSIGNED NOT NULL,
    actor               VARCHAR(128) NOT NULL,
    old_binding_id      BIGINT UNSIGNED NULL,
    new_binding_id      BIGINT UNSIGNED NOT NULL,
    result              VARCHAR(32) NOT NULL,
    reason              VARCHAR(512) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_endpoint_audit_agent_time (agent_id, created_at),
    KEY idx_endpoint_audit_actor_time (actor, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent active endpoint selection audit';

ALTER TABLE fcc_agent DROP INDEX idx_agent_cur_ext, DROP COLUMN current_extension;

-- Repair/rollback notes:
-- The old priority values are retained, so is_active can be rebuilt from priority if required.
-- Do not drop the new uniqueness indexes while mixed application versions are writing.
-- A rollback must first stop writers, copy assigned_node_id back to target_node_id, and supply a
-- non-null placeholder only for legacy code that still incorrectly requires a target node.
