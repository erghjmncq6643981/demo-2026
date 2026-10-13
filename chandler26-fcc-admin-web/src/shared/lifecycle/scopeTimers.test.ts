import { afterEach, expect, it, vi } from 'vitest';
import { createScopeTimers } from './scopeTimers';

afterEach(() => { vi.useRealTimers(); });
it('owns timers and cancels feedback after disposal', () => {
  vi.useFakeTimers();
  const timers = createScopeTimers();
  const callback = vi.fn();
  timers.schedule(callback, 2000);
  timers.dispose();
  timers.schedule(callback, 1000);
  vi.advanceTimersByTime(3000);
  expect(callback).not.toHaveBeenCalled();
  expect(vi.getTimerCount()).toBe(0);
});

it('removes completed callbacks and can dispose repeatedly', () => {
  vi.useFakeTimers();
  const timers = createScopeTimers();
  const callback = vi.fn();
  timers.schedule(callback, 2000);
  vi.advanceTimersByTime(2000);
  expect(callback).toHaveBeenCalledOnce();
  timers.dispose();
  timers.dispose();
  expect(vi.getTimerCount()).toBe(0);
});
