import { describe, expect, it } from 'vitest';
import { initialCallLifecycle, reduceCallLifecycle } from './callStateMachine';

describe('call lifecycle', () => {
  it('returns idle immediately and permits the next call without summary completion', () => {
    let lifecycle = reduceCallLifecycle(initialCallLifecycle(), { type: 'INCOMING', callId: 'call-1' });
    lifecycle = reduceCallLifecycle(lifecycle, { type: 'ANSWERED', callId: 'call-1' });
    lifecycle = reduceCallLifecycle(lifecycle, { type: 'ENDED', callId: 'call-1' });

    expect(reduceCallLifecycle(lifecycle, { type: 'ANSWERED', callId: 'call-1' })).toEqual(lifecycle);
    expect(reduceCallLifecycle(lifecycle, { type: 'ENDED', callId: 'call-1' })).toEqual(lifecycle);
    expect(lifecycle.state).toBe('IDLE');
    expect(reduceCallLifecycle(lifecycle, { type: 'INCOMING', callId: 'call-2' }).state).toBe('RINGING');
  });

  it('does not allow another call to mutate an active lifecycle', () => {
    const ringing = reduceCallLifecycle(initialCallLifecycle(), { type: 'INCOMING', callId: 'call-1' });
    expect(reduceCallLifecycle(ringing, { type: 'ANSWERED', callId: 'call-2' })).toEqual(ringing);
    expect(reduceCallLifecycle(ringing, { type: 'ENDED', callId: 'call-2' })).toEqual(ringing);
    expect(reduceCallLifecycle(ringing, { type: 'ENDED' })).toEqual(ringing);
  });

  it('waits for a terminal transport event after a hangup request', () => {
    let lifecycle = reduceCallLifecycle(initialCallLifecycle(), { type: 'OUTBOUND_STARTED', callId: 'call-1' });
    lifecycle = reduceCallLifecycle(lifecycle, { type: 'ANSWERED', callId: 'call-1' });
    lifecycle = reduceCallLifecycle(lifecycle, { type: 'HANGUP_REQUESTED', callId: 'call-1' });
    expect(lifecycle.state).toBe('ENDING');

    lifecycle = reduceCallLifecycle(lifecycle, { type: 'ENDED', callId: 'call-1' });
    expect(lifecycle.state).toBe('IDLE');
  });
});
