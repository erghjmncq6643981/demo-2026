import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { handleUnauthorizedSession } from './apiClient';

describe('apiClient unauthorized handling', () => {
  const store: Record<string, string> = {};
  const listeners: Record<string, ((event: any) => void)[]> = {};

  beforeEach(() => {
    Object.keys(store).forEach(k => delete store[k]);
    Object.keys(listeners).forEach(k => delete listeners[k]);

    (globalThis as any).localStorage = {
      getItem: (key: string) => store[key] || null,
      setItem: (key: string, value: string) => { store[key] = value; },
      removeItem: (key: string) => { delete store[key]; },
      clear: () => { Object.keys(store).forEach(k => delete store[k]); },
    };

    (globalThis as any).CustomEvent = class {
      type: string;
      detail: any;
      constructor(type: string, init?: any) {
        this.type = type;
        this.detail = init?.detail;
      }
    };

    (globalThis as any).window = {
      addEventListener: (type: string, fn: any) => {
        listeners[type] = listeners[type] || [];
        listeners[type].push(fn);
      },
      removeEventListener: (type: string, fn: any) => {
        listeners[type] = (listeners[type] || []).filter(item => item !== fn);
      },
      dispatchEvent: (event: any) => {
        (listeners[event.type] || []).forEach(fn => fn(event));
        return true;
      },
    };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('handleUnauthorizedSession clears localStorage and dispatches fcc-auth-unauthorized event', () => {
    globalThis.localStorage.setItem('satoken', 'test-token');
    globalThis.localStorage.setItem('fcc_admin_user', JSON.stringify({ loginId: 'admin' }));

    let eventFired = false;
    let eventDetail: any = null;
    const listener = (event: any) => {
      eventFired = true;
      eventDetail = event.detail;
    };

    globalThis.window.addEventListener('fcc-auth-unauthorized', listener);
    try {
      handleUnauthorizedSession('登录已失效，请重新登录');

      expect(globalThis.localStorage.getItem('satoken')).toBeNull();
      expect(globalThis.localStorage.getItem('fcc_admin_user')).toBeNull();
      expect(eventFired).toBe(true);
      expect(eventDetail?.message).toBe('登录已失效，请重新登录');
    } finally {
      globalThis.window.removeEventListener('fcc-auth-unauthorized', listener);
    }
  });
});
