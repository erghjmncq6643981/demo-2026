import { describe, expect, it } from 'vitest';
import { SessionCallRegistry } from './sessionCallRegistry';

describe('SIP session and business call registry', () => {
  it('binds an early business call to the next SIP session', () => {
    const registry = new SessionCallRegistry();
    expect(registry.bindBusinessCall('business-1')).toBeNull();

    const result = registry.register('session-1', 'incoming');
    expect(result.accepted).toBe(true);
    expect(result.binding?.callId).toBe('business-1');
  });

  it('binds a provisional session after SCREEN_POP arrives', () => {
    const registry = new SessionCallRegistry();
    registry.register('session-1', 'incoming');

    expect(registry.bindBusinessCall('business-1')).toMatchObject({
      sessionId: 'session-1',
      callId: 'business-1',
    });
  });

  it('rejects a second session while one session is active', () => {
    const registry = new SessionCallRegistry();
    registry.register('session-1', 'incoming');

    expect(registry.register('session-2', 'incoming').accepted).toBe(false);
    expect(registry.getActive()?.sessionId).toBe('session-1');
  });

  it('does not let a late terminal event remove a newer session', () => {
    const registry = new SessionCallRegistry();
    registry.register('session-1', 'incoming');
    registry.finish('session-1');
    registry.register('session-2', 'incoming');

    expect(registry.finish('session-1').wasActive).toBe(false);
    expect(registry.getActive()?.sessionId).toBe('session-2');
  });

  it('does not rebind an active session to a different business call', () => {
    const registry = new SessionCallRegistry();
    registry.bindBusinessCall('business-1');
    registry.register('session-1', 'incoming');

    expect(registry.bindBusinessCall('business-2')).toBeNull();
    expect(registry.getActive()?.callId).toBe('business-1');
  });
});
