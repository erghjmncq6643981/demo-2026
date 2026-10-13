import { effectScope, ref } from 'vue';
import { expect, it, vi } from 'vitest';
import { useCallJourney } from './useCallJourney';
import type { CallRecord } from '../../cdr/model/callRecord';
import type { StageExecution } from '../model/stagedFlow';

vi.mock('./useCallExecution', () => ({ useCallExecution: () => ({
  flow: ref(null), executions: ref([]), error: ref(''), loading: ref(false),
  cursor: ref(''), status: ref('ENDED'), version: ref(''), load: vi.fn(),
}) }));

it('ended/answered CDR alone never establishes binding or stage success', () => {
  const scope = effectScope();
  const journey = scope.run(() => useCallJourney({
    callId: '9007199254740993', cdr: { status: 'ANSWERED', flowCode: 'SYSTEM_PHONE_BINDING' } as CallRecord,
  }))!;
  expect(journey.isBindingAccepted.value).toBeUndefined();
  expect(journey.journeyCards.value.every(card => card.statusText === '暂无执行记录')).toBe(true);
  expect(journey.journeyCards.value.every(card => card.durationText === '-')).toBe(true);
  scope.stop();
});

it('notification uses its recorded stages and the latest attempt instead of an agent journey', () => {
  const scope = effectScope();
  const journey = scope.run(() => useCallJourney({
    callId: '9007199254740993', cdr: { flowCode: 'SYSTEM_NOTIFICATION' } as CallRecord,
  }))!;
  journey.executions.value = [
    { stepKey: 'PLAY_ONLY', attemptNo: 1, status: 'WAITING' },
    { stepKey: 'PLAY_ONLY', attemptNo: 2, status: 'FAILED', errorCode: 'MEDIA_ERROR' },
  ] as StageExecution[];
  expect(journey.journeyCards.value.map(card => card.key)).toEqual(['PLAY_ONLY']);
  expect(journey.journeyCards.value[0]?.statusText).toBe('失败');
  journey.selected.value = 'PLAY_ONLY';
  expect(journey.selectedAttempts.value).toHaveLength(2);
  scope.stop();
});
