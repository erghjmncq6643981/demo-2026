import { afterEach, describe, expect, it, vi } from 'vitest';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('FCC API deployment addresses', () => {
  it('uses the hosted domain for the separate HTTP services', async () => {
    vi.stubGlobal('window', { location: { protocol: 'http:', hostname: 'fcc.local' } });
    vi.stubGlobal('localStorage', { getItem: () => 'localhost' });

    const { adminApi, telephonyApi } = await import('./apiClient');
    expect(adminApi.defaults.baseURL).toBe('http://fcc.local:8089/api/admin');
    expect(telephonyApi.defaults.baseURL).toBe('http://fcc.local:8085/api/telephony');
  });

  it('uses same-origin API paths for HTTPS deployments', async () => {
    vi.stubGlobal('window', { location: { protocol: 'https:', hostname: 'fcc.example.com' } });
    vi.stubGlobal('localStorage', { getItem: () => null });

    const { adminApi, telephonyApi } = await import('./apiClient');
    expect(adminApi.defaults.baseURL).toBe('/api/admin');
    expect(telephonyApi.defaults.baseURL).toBe('/api/telephony');
  });
});
