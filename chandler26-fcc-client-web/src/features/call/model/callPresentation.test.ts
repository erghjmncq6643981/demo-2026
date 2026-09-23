import { describe, expect, it } from 'vitest';
import { callStateLabel, callStateTone } from './callPresentation';

describe('callPresentation', () => {
  it('keeps agent presence out of the call lifecycle', () => {
    expect(callStateLabel('IDLE', 'READY')).toBe('空闲');
    expect(callStateLabel('CONNECTED', 'BUSY')).toBe('通话中');
    expect(callStateLabel('IDLE', 'ACW')).toBe('话后整理');
  });

  it('shows server occupation only as a reconnect fallback', () => {
    expect(callStateLabel('IDLE', 'ANSWERED')).toContain('恢复中');
    expect(callStateTone('IDLE', 'ANSWERED')).toBe('danger');
    expect(callStateTone('IDLE', 'BUSY')).toBe('neutral');
  });
});
