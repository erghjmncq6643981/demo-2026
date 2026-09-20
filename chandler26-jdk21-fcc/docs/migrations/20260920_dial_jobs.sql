-- Apply once to existing database after backup; baseline already includes these columns.
-- Existing jobs have no owner and are not automatically dispatched. Assign only after reviewing their provenance.
ALTER TABLE fcc_dial_job ADD COLUMN owner_work_no VARCHAR(64) NULL AFTER tenant_id;
ALTER TABLE fcc_dial_job ADD UNIQUE KEY uk_dial_job_request(tenant_id,owner_work_no,biz_id);
CREATE TABLE IF NOT EXISTS fcc_dial_scheduler_lock(id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO fcc_dial_scheduler_lock(id) VALUES(1);
-- Application rollback: disable dispatcher first; retain rows and new nullable column for reconciliation.
