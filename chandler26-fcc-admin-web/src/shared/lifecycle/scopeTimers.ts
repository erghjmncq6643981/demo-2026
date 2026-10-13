import { onScopeDispose } from 'vue';

/** Own scheduled UI feedback callbacks; disposal prevents stale async work adding timers. */
export function createScopeTimers() {
  const pending = new Set<ReturnType<typeof setTimeout>>();
  let disposed = false;
  function schedule(callback: () => void, delay: number) {
    if (disposed) return;
    const timer = setTimeout(() => {
      pending.delete(timer);
      if (!disposed) callback();
    }, delay);
    pending.add(timer);
  }
  function dispose() {
    disposed = true;
    pending.forEach(clearTimeout);
    pending.clear();
  }
  return { schedule, dispose };
}

/** Attach UI timers to the feature composable's Vue scope. */
export function useScopeTimers() {
  const timers = createScopeTimers();
  onScopeDispose(timers.dispose);
  return timers;
}
