import test from 'node:test';
import assert from 'node:assert/strict';
import { parseFlowDefinition } from '../src/features/flows/model/flowDefinition.ts';
import { buildTree } from '../src/features/groups/model/groupTree.ts';
import { toCallRecord } from '../src/features/cdr/model/callRecord.ts';

test('CDR missing measurements remain unavailable and zero duration is preserved', () => {
  const source = { id: '9223372036854775806', ctrlId: 'control', status: 'NO_ANSWER', caller: 'test', direction: 'INBOUND' };
  const missing = toCallRecord(source);
  assert.equal(missing.rawId, source.id);
  assert.equal(missing.ringDuration, '-');
  assert.equal(missing.audioDuration, undefined);
  assert.equal(missing.satisfactionScore, undefined);
  assert.equal(missing.callerName, '');
  assert.equal(toCallRecord({ ...source, waitDurationMs: 0 }).ringDuration, '0秒');
});

test('flow editor rejects invalid JSON, arrays and null', () => {
  for (const input of ['', '{', '[]', 'null', '"text"']) {
    assert.throws(() => parseFlowDefinition(input));
  }
  assert.deepEqual(parseFlowDefinition('{"nodes":[]}'), { nodes: [] });
});

test('group tree preserves large string IDs and resolves child-first ordering', () => {
  const groups = [
    { id: '9223372036854775806', parentId: '9223372036854775805', groupName: '叶节点' },
    { id: '9223372036854775805', parentId: '9223372036854775804', groupName: '部门' },
    { id: '9223372036854775804', groupName: '根节点' },
  ];
  const tree = buildTree(groups);
  assert.equal(tree[0].id, groups[2].id);
  assert.equal(tree[0].children[0].children[0].id, groups[0].id);
  assert.equal(tree[0].children[0].children[0].level, 2);
});

test('group tree rejects cycles rather than recursing indefinitely', () => {
  assert.throws(() => buildTree([
    { id: 'a', parentId: 'b', groupName: 'A' },
    { id: 'b', parentId: 'a', groupName: 'B' },
  ]), /循环/);
});
