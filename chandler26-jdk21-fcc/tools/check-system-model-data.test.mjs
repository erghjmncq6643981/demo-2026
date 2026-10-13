import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../', import.meta.url);
const models = JSON.parse(readFileSync(new URL('fcc-common/src/main/resources/flows/system-models.json', root), 'utf8'));
const baseline = readFileSync(new URL('docs/fcc-schema.sql', root), 'utf8');
const upgrade = readFileSync(new URL('docs/upgrades/2026-10-04-notification-model-v2.sql', root), 'utf8');

test('baseline catalog matches canonical model JSON and checksum', () => {
  for (const model of Object.values(models)) {
    const json = JSON.stringify(model);
    assert.ok(baseline.includes(Buffer.from(json).toString('hex')), model.template);
    assert.ok(baseline.includes(createHash('sha256').update(json).digest('hex')), model.template);
  }
});

test('existing catalog upgrade appends V2 without rewriting definitions or historical snapshots', () => {
  const json = JSON.stringify(models.NOTIFICATION);
  assert.ok(upgrade.includes(Buffer.from(json).toString('hex')));
  assert.ok(upgrade.includes(createHash('sha256').update(json).digest('hex')));
  assert.ok(upgrade.includes('d.current_version=1 AND old.checksum='));
  assert.ok(upgrade.includes('NOT EXISTS'));
  assert.ok(upgrade.indexOf("SET old.publish_status='ARCHIVED'") < upgrade.indexOf("SET v.publish_status='PUBLISHED'"));
  assert.doesNotMatch(upgrade, /SET\s+(?:\w+\.)?definition_json|DELETE\s+FROM|UPDATE\s+fcc_flow_instance/i);
});
