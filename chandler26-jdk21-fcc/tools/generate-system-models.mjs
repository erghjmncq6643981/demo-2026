import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

// 根据唯一模型资源机械生成全新数据库基线中的系统流程模型。
const root = new URL('../', import.meta.url);
const models = JSON.parse(readFileSync(new URL('fcc-common/src/main/resources/flows/system-models.json', root), 'utf8'));
const names = { INBOUND: '系统呼入', AGENT_FIRST: '坐席先接听外呼', NOTIFICATION: '通知外呼', PHONE_BINDING: '话机绑定' };
const literal = value => `CONVERT(0x${Buffer.from(value, 'utf8').toString('hex')} USING utf8mb4)`;
const statements = [
  '-- Generated from fcc-common/src/main/resources/flows/system-models.json.',
  '-- Fresh-install seed for the single-call-center schema.',
  'START TRANSACTION;'
];
for (const [index, [key, model]] of Object.entries(models).entries()) {
  const definitionId = `82000000000000000${index + 1}`;
  const versionId = `82000000000000002${index + 1}`;
  const version = 1;
  const json = JSON.stringify(model);
  const checksum = createHash('sha256').update(json).digest('hex');
  statements.push(`INSERT INTO fcc_flow_definition(id,flow_key,flow_name,model_type,status,current_version)
SELECT ${definitionId},'SYSTEM_${key}',${literal(names[key])},'${key}','PUBLISHED',${version}
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
