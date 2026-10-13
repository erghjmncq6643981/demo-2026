import { effectScope } from 'vue';
import { expect, it, vi } from 'vitest';
import { useReportData } from './useReportData';
import type { CallCdrVO } from '../../../api/cdrApi';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: Error) => void;
  const promise = new Promise<T>((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}

it('distinguishes empty success, failed refresh and a successful retry', async () => {
  const fetch = vi.fn().mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('登录状态已失效')).mockResolvedValueOnce([{ id: '9007199254740993' }]);
  const scope = effectScope();
  const data = scope.run(() => useReportData(fetch))!;
  await data.loadData();
  expect(data.hasLoaded.value).toBe(true);
  expect(data.loadError.value).toBe('');
  await data.loadData();
  expect(data.hasLoaded.value).toBe(false);
  expect(data.loadError.value).toBe('登录状态已失效');
  expect(data.rawCdrs.value).toEqual([]);
  await data.loadData();
  expect(data.rawCdrs.value[0]?.id).toBe('9007199254740993');
  expect(data.loadError.value).toBe('');
  scope.stop();
});

it('late failure cannot overwrite the newest period', async () => {
  const old = deferred<CallCdrVO[]>(), latest = deferred<CallCdrVO[]>();
  const fetch = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
  const scope = effectScope();
  const data = scope.run(() => useReportData(fetch))!;
  const first = data.loadData(), second = data.loadData();
  latest.resolve([{ id: 'new' } as CallCdrVO]); await second;
  old.reject(new Error('old error')); await first;
  expect(data.rawCdrs.value[0]?.id).toBe('new');
  expect(data.loadError.value).toBe('');
  expect(data.isLoading.value).toBe(false);
  scope.stop();
});

it('late success cannot stop a newer load and ignores disposal results', async () => {
  const old = deferred<CallCdrVO[]>(), latest = deferred<CallCdrVO[]>();
  const scope = effectScope();
  const data = scope.run(() => useReportData(vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)))!;
  const first = data.loadData(), second = data.loadData();
  old.resolve([{ id: 'old' } as CallCdrVO]); await first;
  expect(data.rawCdrs.value).toEqual([]);
  expect(data.isLoading.value).toBe(true);
  scope.stop();
  latest.resolve([{ id: 'disposed' } as CallCdrVO]); await second;
  expect(data.rawCdrs.value).toEqual([]);
});
