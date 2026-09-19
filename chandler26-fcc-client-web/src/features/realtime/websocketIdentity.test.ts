import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { wsService } from '../../services/websocketService';

vi.mock('../../shared/config/runtimeConfig', () => ({ getRuntimeConfig: () => ({ agentWebSocketUrl: 'wss://fcc.example/ws/agent' }) }));
const connections: Array<{ url: string; protocols: string[] }> = [];

beforeEach(() => {
  vi.stubGlobal('window', globalThis);
  vi.stubGlobal('localStorage', { getItem: () => 'test-only-token' });
  vi.stubGlobal('WebSocket', class {
    onclose: unknown = null;
    constructor(url: string, protocols: string[]) { connections.push({ url, protocols }); }
    close() { }
  });
  connections.length = 0;
});
afterEach(() => { wsService.disconnect(); vi.unstubAllGlobals(); });

it('sends identity proof in a non-echoed protocol header, never in the URL', () => {
  wsService.connect('untrusted-work-number');
  expect(connections).toHaveLength(1);
  expect(connections[0].url).toBe('wss://fcc.example/ws/agent');
  expect(connections[0].protocols[0]).toBe('fcc-agent');
  expect(connections[0].protocols[1]).toMatch(/^auth\./);
  expect(wsService.connectionUrl.value).not.toContain('test-only-token');
});

it('does not open a socket without a login token', () => {
  vi.stubGlobal('localStorage', { getItem: () => null });
  wsService.connect('untrusted-work-number');
  expect(connections).toHaveLength(0);
});
