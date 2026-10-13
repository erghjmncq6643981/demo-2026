import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useCallStore } from '../../../stores/callStore';

const mocks = vi.hoisted(() => ({
  hold: vi.fn(),
  hangup: vi.fn(),
  dtmf: vi.fn(),
  sipDtmf: vi.fn(),
  sipHangup: vi.fn(),
  sipAnswer: vi.fn(),
  sipMute: vi.fn(),
  sipBind: vi.fn(),
  sipRelease: vi.fn(),
  summary: vi.fn(),
}));
vi.mock('../../../api/telephonyApi', () => ({ triggerHoldCall: mocks.hold, triggerHangupCall: mocks.hangup, triggerDtmfCall: mocks.dtmf }));
vi.mock('../../../services/sipWebRtcService', () => ({
  sipWebRtcService: {
    hangup: mocks.sipHangup,
    answer: mocks.sipAnswer,
    toggleMute: mocks.sipMute,
    bindBusinessCall: mocks.sipBind,
    releasePendingBusinessCall: mocks.sipRelease,
    sendDtmf: mocks.sipDtmf,
    getActiveContext: vi.fn(),
  },
}));
vi.mock('../../../services/audioService', () => ({ audioService: { startRingtone: vi.fn(), stopRingtone: vi.fn() } }));
vi.mock('../../../utils/feedback', () => ({ toast: vi.fn(), toastError: vi.fn() }));
vi.mock('../api/callSummaryApi', () => ({ submitCallSummary: mocks.summary }));

describe('call command outcomes', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    vi.stubGlobal('window', globalThis);
    vi.stubGlobal('localStorage', { getItem: () => 'agent-test' });
    setActivePinia(createPinia());
  });
  afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals(); });

  function connected() {
    const store = useCallStore();
    store.triggerIncoming({ callId: 'call-test' });
    store.observeAnswered('call-test');
    return store;
  }

  it('failed hangup preserves the active call and does not locally disconnect SIP', async () => {
    const store = connected();
    mocks.hangup.mockRejectedValueOnce(new Error('rejected'));
    await store.hangupCall();
    expect(store.callState).toBe('CONNECTED');
    expect(store.currentCall?.callId).toBe('call-test');
    expect(mocks.sipHangup).not.toHaveBeenCalled();
  });

  it('ignores uncorrelated and old participation notifications while a new call is connected', () => {
    const store = connected();
    store.observeReleased('old-call');
    store.observeReleased();
    expect(store.callState).toBe('CONNECTED');
    expect(store.currentCall?.callId).toBe('call-test');
  });

  it('releases immediately and deduplicates summary facts and late screen pops', () => {
    const store = connected();
    store.observeEnded('call-test');
    store.observeEnded('call-test');
    store.observeAnswered('call-test');
    store.observeReleased('old-call');
    expect(store.callState).toBe('IDLE');
    store.observeReleased('call-test');
    expect(store.callState).toBe('IDLE');
    expect(store.currentCall).toBeNull();
    expect(store.summaryCalls).toHaveLength(1);
    store.triggerIncoming({ callId: 'call-test' });
    expect(store.callState).toBe('IDLE');
    store.triggerIncoming({ callId: 'new-call' });
    store.observeAnswered('new-call');
    store.observeEnded('call-test');
    expect(store.currentCall?.callId).toBe('new-call');
    expect(store.callState).toBe('CONNECTED');
  });

  it('saving the old summary cannot clear a new connected call', async () => {
    const store = connected();
    store.observeEnded('call-test');
    let complete!: () => void;
    mocks.summary.mockReturnValueOnce(new Promise<void>(resolve => { complete = resolve; }));
    const saving = store.saveSummary({ category: '咨询', intent: 'HIGH', notes: '旧通话' });
    store.triggerIncoming({ callId: 'new-call' });
    store.observeAnswered('new-call');
    complete(); await saving;
    expect(mocks.summary).toHaveBeenCalledWith('call-test', expect.any(Object));
    expect(store.callState).toBe('CONNECTED');
    expect(store.currentCall?.callId).toBe('new-call');
    expect(store.summaryCalls).toHaveLength(0);
  });

  it('ignores an old summary response after resetting the login context', async () => {
    const store = connected();
    store.observeEnded('call-test');
    let complete!: () => void;
    mocks.summary.mockReturnValueOnce(new Promise<void>(resolve => { complete = resolve; }));
    const saving = store.saveSummary();
    store.resetCall();
    store.triggerIncoming({ callId: 'next-login-call' });
    store.observeEnded('next-login-call');
    complete();
    expect(await saving).toBe(false);
    expect(store.summaryCall?.callId).toBe('next-login-call');
    expect(store.showSummaryDrawer).toBe(true);
  });

  it('failed summary remains retryable without reoccupying the agent', async () => {
    const store = connected();
    store.observeEnded('call-test');
    mocks.summary.mockRejectedValueOnce(new Error('稍后重试'));
    expect(await store.saveSummary()).toBe(false);
    expect(store.callState).toBe('IDLE');
    expect(store.summaryCall?.callId).toBe('call-test');
    store.triggerIncoming({ callId: 'new-call' });
    expect(store.showSummaryDrawer).toBe(false);
  });

  it('disposes the call-duration timer with its store scope', () => {
    const store = connected();
    vi.advanceTimersByTime(2000);
    expect(store.durationSeconds).toBe(2);
    store.$dispose();
    vi.advanceTimersByTime(2000);
    expect(store.durationSeconds).toBe(2);
  });

  it('acknowledged hold is not reported as confirmed media hold', async () => {
    const store = connected();
    mocks.hold.mockResolvedValueOnce({ code: 200, message: '待确认', data: { status: 'ACCEPTED' } });
    await store.toggleHold();
    expect(store.isHeld).toBe(false);
    expect(store.holdRequested).toBe(true);
    expect(store.controlMessage).toBe('待确认');
    mocks.hold.mockRejectedValueOnce(new Error('failed'));
    await store.toggleHold();
    expect(store.holdRequested).toBe(true);
  });

  it('uses only the control-plane DTMF path', async () => {
    const store = connected();
    mocks.dtmf.mockResolvedValueOnce({ code: 200 });
    await store.sendDtmf('1');
    expect(mocks.dtmf).toHaveBeenCalledOnce();
    expect(mocks.sipDtmf).not.toHaveBeenCalled();
  });

  it('changes mute state only after the media operation succeeds', () => {
    const store = connected();
    mocks.sipMute.mockReturnValueOnce(false).mockReturnValueOnce(true);

    store.toggleMute();
    expect(store.isMuted).toBe(false);
    store.toggleMute();
    expect(store.isMuted).toBe(true);
  });

  it('keeps the outbound call ID and merges its later screen-pop facts', () => {
    const store = useCallStore();
    store.startOutbound('business-outbound', 'customer-number');
    store.triggerIncoming({
      callId: 'business-outbound',
      direction: 'OUTBOUND',
      callerNumber: 'customer-number',
      customerName: '已确认客户',
    });

    expect(store.callState).toBe('CALLING');
    expect(store.currentCall).toMatchObject({
      callId: 'business-outbound',
      customerName: '已确认客户',
    });
  });
});
