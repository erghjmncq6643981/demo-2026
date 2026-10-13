import { expect, it } from 'vitest';
import { bindingOutcome, executionEvidence } from './journeyEvidence';
import type { StageExecution } from './stagedFlow';

it('does not invent success, source time or duration for missing execution', () => {
  expect(executionEvidence()).toMatchObject({ statusText: '暂无执行记录', startedAt: '-', durationText: '-' });
  expect(bindingOutcome({})).toBeUndefined();
  expect(bindingOutcome({ bindingAccepted: 'false' })).toBeUndefined();
  expect(bindingOutcome({ bindingAccepted: false })).toBe(false);
});

it('presents failed, waiting, unknown and command success without telephony inference', () => {
  const step = { status: 'FAILED', durationMs: 0, startedAt: 'source-time' } as StageExecution;
  expect(executionEvidence(step)).toMatchObject({ statusText: '失败', durationText: '0 ms' });
  expect(executionEvidence({ ...step, status: 'WAITING' }).statusText).toBe('等待事件');
  expect(executionEvidence({ ...step, status: 'SUCCEEDED' }).statusText).toBe('已完成');
  expect(executionEvidence({ ...step, status: 'NEW_STATUS' }).statusText).toContain('NEW_STATUS');
  expect(bindingOutcome({ bindingAccepted: true })).toBe(true);
});
