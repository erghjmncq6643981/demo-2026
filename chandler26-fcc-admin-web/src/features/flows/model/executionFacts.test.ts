import { expect, it } from 'vitest';
import { collectExecutionFacts } from './executionFacts';

it('preserves persisted boundary order and opaque identifiers', () => {
  expect(collectExecutionFacts([
    { output: '{"callId":"9007199254740993","result":"output"}', input: '{"result":"input"}' },
    { output: '{"status":"ENDED"}' },
  ])).toEqual({ callId: '9007199254740993', result: 'input', status: 'ENDED' });
});

it('does not invent facts when boundaries are absent or malformed', () => {
  expect(collectExecutionFacts([{ input: 'invalid' }, {}])).toEqual({});
});
