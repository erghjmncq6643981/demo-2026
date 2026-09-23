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
  },
}));
vi.mock('../../../services/audioService', () => ({ audioService: { startRingtone: vi.fn(), stopRingtone: vi.fn() } }));
vi.mock('../../../utils/feedback', () => ({ toast: vi.fn(), toastError: vi.fn() }));

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
