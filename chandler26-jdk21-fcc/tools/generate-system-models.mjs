import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

// 根据唯一模型资源机械生成全新数据库基线中的系统流程模型。
const root = new URL('../', import.meta.url);
const models = JSON.parse(readFileSync(new URL('fcc-common/src/main/resources/flows/system-models.json', root), 'utf8'));
const systemModels = [
  { key: 'INBOUND', name: '系统呼入', definitionId: '820000000000000001', versionId: '820000000000000021' },
  { key: 'AGENT_FIRST', name: '系统先呼坐席外呼', definitionId: '820000000000000002', versionId: '820000000000000022' },
  { key: 'NOTIFICATION', name: '通知外呼', definitionId: '820000000000000003', versionId: '820000000000000023' },
  { key: 'PHONE_BINDING', name: '话机绑定', definitionId: '820000000000000004', versionId: '820000000000000024' },
  { key: 'AGENT_ORIGINATED', name: '坐席终端主动外呼', definitionId: '820000000000000005', versionId: '820000000000000025' }
];
const literal = value => `CONVERT(0x${Buffer.from(value, 'utf8').toString('hex')} USING utf8mb4)`;
const statements = [
  '-- Generated from fcc-common/src/main/resources/flows/system-models.json.',
  '-- Fresh-install seed for the single-call-center schema.',
  'START TRANSACTION;'
];
for (const { key, name, definitionId, versionId } of systemModels) {
  const model = models[key];
  if (!model) {
    throw new Error(`Missing system flow model: ${key}`);
  }
  const version = 1;
  const json = JSON.stringify(model);
  const checksum = createHash('sha256').update(json).digest('hex');
  statements.push(`INSERT INTO fcc_flow_definition(id,flow_key,flow_name,model_type,status,current_version)
SELECT ${definitionId},'SYSTEM_${key}',${literal(name)},'${key}','PUBLISHED',${version}
WHERE NOT EXISTS (SELECT 1 FROM fcc_flow_definition WHERE id=${definitionId});
INSERT INTO fcc_flow_definition_version(id,flow_definition_id,version_no,definition_json,checksum,publish_status,published_at,created_by)
SELECT ${versionId},${definitionId},${version},${literal(json)},'${checksum}','PUBLISHED',UTC_TIMESTAMP(3),'system'
WHERE NOT EXISTS (SELECT 1 FROM fcc_flow_definition_version WHERE flow_definition_id=${definitionId} AND version_no=${version});
UPDATE fcc_flow_definition SET current_version=${version},status='PUBLISHED' WHERE id=${definitionId};`);
}
statements.push('COMMIT;');
const sql = statements.join('\n\n') + '\n';
const baseline = new URL('docs/fcc-schema.sql', root);
const marker = '-- BEGIN GENERATED COMPLETE SYSTEM MODELS';
const original = readFileSync(baseline, 'utf8').split(marker)[0].trimEnd();
writeFileSync(baseline, `${original}\n\n${marker}\n${sql}-- END GENERATED COMPLETE SYSTEM MODELS\n`);
