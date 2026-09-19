import { describe, expect, it } from 'vitest';
import { resolveAgentWebSocketUrl } from './runtimeConfig';

describe('runtime WebSocket configuration', () => {
  it('uses WSS for a relative endpoint on an HTTPS page', () => {
    expect(resolveAgentWebSocketUrl('/ws/agent', {
      protocol: 'https:',
      host: 'fcc.example.com',
      href: 'https://fcc.example.com/app',
    })).toBe('wss://fcc.example.com/ws/agent');
  });

  it('rejects non-WebSocket absolute URLs', () => {
    expect(() => resolveAgentWebSocketUrl('http://fcc.example.com/ws/agent')).toThrow(/ws:\/\/ or wss:\/\//);
  });
});
