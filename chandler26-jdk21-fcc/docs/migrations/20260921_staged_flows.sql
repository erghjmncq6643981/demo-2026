-- Fixed system templates; no demo accounts, numbers or calls.
-- Apply after 20260920_callback_dispatch.sql, before starting fcc-server.
-- Existing calls are not backfilled: never invent execution history.
-- Rollback: stop traffic and restore prior application; retain execution facts.
INSERT INTO fcc_flow_definition(id,tenant_id,flow_key,flow_name,model_type,status,current_version)
VALUES(820000000000000001,0,'SYSTEM_INBOUND','系统呼入','INBOUND','PUBLISHED',1);
INSERT INTO fcc_flow_definition_version(id,flow_definition_id,version_no,definition_json,checksum,publish_status,published_at,created_by)
VALUES(820000000000000011,820000000000000001,1,'{"template":"INBOUND","stages":["ENTRY","MENU","BRANCH","ROUTE","BRIDGE","CONNECTED","END"]}','system-template-v1','PUBLISHED',UTC_TIMESTAMP(3),'system');
INSERT INTO fcc_flow_definition(id,tenant_id,flow_key,flow_name,model_type,status,current_version)
VALUES(820000000000000002,0,'SYSTEM_AGENT_FIRST','坐席先接听外呼','AGENT_FIRST','PUBLISHED',1);
INSERT INTO fcc_flow_definition_version(id,flow_definition_id,version_no,definition_json,checksum,publish_status,published_at,created_by)
VALUES(820000000000000012,820000000000000002,1,'{"template":"AGENT_FIRST","stages":["ENTRY","DIAL_AGENT","DIAL_CUSTOMER","BRIDGE","CONNECTED","END"]}','system-template-v1','PUBLISHED',UTC_TIMESTAMP(3),'system');
INSERT INTO fcc_flow_definition(id,tenant_id,flow_key,flow_name,model_type,status,current_version)
VALUES(820000000000000003,0,'SYSTEM_NOTIFICATION','通知外呼','NOTIFICATION','PUBLISHED',1);
INSERT INTO fcc_flow_definition_version(id,flow_definition_id,version_no,definition_json,checksum,publish_status,published_at,created_by)
VALUES(820000000000000013,820000000000000003,1,'{"template":"NOTIFICATION","stages":["ENTRY","DIAL_CUSTOMER","NOTIFY","CONFIRM","END"]}','system-template-v1','PUBLISHED',UTC_TIMESTAMP(3),'system');
