import { effectScope, nextTick, ref } from 'vue';
import { expect, it, vi } from 'vitest';
import { useCallExecution } from './useCallExecution';
import { flowApi } from '../../../api/flowApi';

vi.mock('../../../api/flowApi', () => ({ flowApi: { execution: vi.fn() } }));

it('clears old version and terminal status when changing calls or failing', async () => {
  vi.mocked(flowApi.execution).mockResolvedValueOnce({
    instance: { status: 'ENDED', snapshot: JSON.stringify({ definition: {}, flowKey: 'old-call', versionNo: 1 }) },
    steps: [], nextCursor: '',
  } as any).mockRejectedValueOnce(new Error('服务不可用'));
  const scope = effectScope(), callId = ref('old-call');
  const data = scope.run(() => useCallExecution(callId))!;
  await nextTick(); await nextTick();
  expect(data.status.value).toBe('ENDED');
  callId.value = 'new-call';
  await nextTick(); await nextTick();
  expect(data.version.value).toBe('');
  expect(data.status.value).toBe('');
  expect(data.error.value).toBe('服务不可用');
  scope.stop();
});

it('ignores results after disposal', async () => {
  let finish!: (result: any) => void;
  vi.mocked(flowApi.execution).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const scope = effectScope();
  const data = scope.run(() => useCallExecution(ref('9007199254740993')))!;
  scope.stop();
  finish({ instance: { status: 'ENDED' }, steps: [], nextCursor: '' });
  await nextTick();
  expect(data.status.value).toBe('');
});
