import { defineStore } from 'pinia';
import { computed, ref, onScopeDispose } from 'vue';
import type { IncomingScreenPopPayload } from '../../../types/telephony';
import { audioService } from '../../../services/audioService';
import { toast, toastError } from '../../../utils/feedback';
import { sipWebRtcService } from '../../../services/sipWebRtcService';
import { triggerHangupCall, triggerHoldCall, triggerDtmfCall } from '../../../api/telephonyApi';
import { submitCallSummary } from '../api/callSummaryApi';
import { useAgentStore } from '../../../stores/agentStore';
import {
  initialCallLifecycle,
  reduceCallLifecycle,
  type CallLifecycleEvent,
} from '../model/callStateMachine';

export const useCallStore = defineStore('call', () => {
  const lifecycle = ref(initialCallLifecycle());
  const callState = computed(() => lifecycle.value.state);
  const currentCall = ref<IncomingScreenPopPayload | null>(null);
  const durationSeconds = ref(0);
  const isHeld = ref(false);
  const holdRequested = ref(false);
  const holdPending = ref(false);
  const controlMessage = ref('');
  const isMuted = ref(false);
  const showSummaryDrawer = ref(false);
  const summaryCalls = ref<IncomingScreenPopPayload[]>([]);
  const summaryCall = computed(() => summaryCalls.value[0] || null);
  const endedCalls = new Set<string>();

  let callTimerInterval: number | null = null;
  let hangupRequestKey: string | null = null;
  let summaryGeneration = 0;

  function applyLifecycle(event: CallLifecycleEvent): boolean {
    const previous = lifecycle.value;
    const next = reduceCallLifecycle(previous, event);
    lifecycle.value = next;
    return next !== previous;
  }

  function getWorkNo(): string {
    return localStorage.getItem('fcc_agent_workno') || '';
  }

  function stopCallTimer() {
    if (callTimerInterval !== null) {
      clearInterval(callTimerInterval);
      callTimerInterval = null;
    }
  }

  function startCallTimer() {
    stopCallTimer();
    durationSeconds.value = 0;
    callTimerInterval = window.setInterval(() => {
      durationSeconds.value += 1;
    }, 1000);
  }

  function triggerIncoming(payload: IncomingScreenPopPayload) {
    if (endedCalls.has(payload.callId)) return;
    if (currentCall.value?.callId === payload.callId && callState.value !== 'ACW') {
      currentCall.value = { ...currentCall.value, ...payload };
      return;
    }
    if (callState.value !== 'IDLE') return;
    const event: CallLifecycleEvent = payload.direction === 'OUTBOUND'
      ? { type: 'OUTBOUND_STARTED', callId: payload.callId }
      : { type: 'INCOMING', callId: payload.callId };
    if (!applyLifecycle(event)) return;
    sipWebRtcService.bindBusinessCall(payload.callId);
    currentCall.value = payload;
    showSummaryDrawer.value = false;
    controlMessage.value = '';
    isMuted.value = false;
    if (event.type === 'INCOMING') audioService.startRingtone();
    if (payload.direction === 'OUTBOUND' && useAgentStore().endpoint === 'WEBRTC') {
      if (sipWebRtcService.sessionState.value === 'RINGING') {
        void answerCall();
      }
    }
  }

  function handleIncomingSipSession(callId?: string) {
    const isOutbound = callState.value === 'CALLING'
      || currentCall.value?.direction === 'OUTBOUND'
      || Boolean(callId && currentCall.value?.callId === callId);
    if (isOutbound && useAgentStore().endpoint === 'WEBRTC') {
      void answerCall();
    }
  }

  function resetCall() {
    summaryGeneration += 1;
    audioService.stopRingtone();
    stopCallTimer();
    lifecycle.value = initialCallLifecycle();
    currentCall.value = null;
    durationSeconds.value = 0;
    isHeld.value = false;
    isMuted.value = false;
    holdRequested.value = false;
    controlMessage.value = '';
    showSummaryDrawer.value = false;
    summaryCalls.value = [];
    endedCalls.clear();
    sipWebRtcService.hangup();
  }

  function startOutbound(callId: string, calleeNumber?: string) {
    if (endedCalls.has(callId)) return;
    if (callState.value !== 'IDLE' && currentCall.value?.callId !== callId) return;
    sipWebRtcService.bindBusinessCall(callId);
    if (!applyLifecycle({ type: 'OUTBOUND_STARTED', callId })) {
      if (lifecycle.value.state === 'CALLING') {
        lifecycle.value = { state: 'CALLING', callId };
      }
    }
    currentCall.value = {
      callId,
      direction: 'OUTBOUND',
      callerNumber: calleeNumber || currentCall.value?.callerNumber,
    };
    showSummaryDrawer.value = false;
    controlMessage.value = '';
    isMuted.value = false;
    if (useAgentStore().endpoint === 'WEBRTC') {
      const ctx = sipWebRtcService.getActiveContext();
      if (ctx && ctx.session.direction === 'incoming') {
        void answerCall();
      }
    }
  }

  async function answerCall() {
    const result = await sipWebRtcService.answer();
    if (!result.ok) {
      toastError(result.message || '软电话接听失败');
      return false;
    }
    audioService.stopRingtone();
    return true;
  }

  function observeAnswered(callId?: string) {
    if (!applyLifecycle({ type: 'ANSWERED', callId })) return;
    audioService.stopRingtone();
    isHeld.value = false;
    holdRequested.value = false;
    controlMessage.value = '';
    startCallTimer();
  }

  async function rejectCall() {
    await hangupCall('USER_REJECT');
  }

  async function hangupCall(reason?: string) {
    const callId = currentCall.value?.callId;
    if (!callId || callState.value === 'ACW' || callState.value === 'ENDING' || hangupRequestKey) return;
    if (callId === 'outbound-pending') {
      resetCall();
      return;
    }
    hangupRequestKey = callId;
    try {
      const workNo = getWorkNo();
      if (!workNo) throw new Error('登录身份不可用');
      const result = await triggerHangupCall(workNo, callId, reason || 'NORMAL_CLEARING');
      if (currentCall.value?.callId !== callId) return;
      controlMessage.value = result.message;
      audioService.stopRingtone();
      applyLifecycle({ type: 'HANGUP_REQUESTED', callId });
      sipWebRtcService.hangup();
    } catch (error) {
      controlMessage.value = error instanceof Error ? error.message : '挂机请求失败';
      toastError(controlMessage.value);
    } finally { hangupRequestKey = null; }
  }

  function observeEnded(callId?: string) {
    if (!callId || currentCall.value?.callId !== callId) return;
    const ended = currentCall.value;
    if (callId) sipWebRtcService.releasePendingBusinessCall(callId);
    if (!applyLifecycle({ type: 'ENDED', callId })) return;
    audioService.stopRingtone();
    stopCallTimer();
    endedCalls.add(callId);
    if (endedCalls.size > 256) endedCalls.delete(endedCalls.values().next().value!);
    if (!summaryCalls.value.some(call => call.callId === callId)) summaryCalls.value.push({ ...ended });
    currentCall.value = null;
    durationSeconds.value = 0;
    isHeld.value = false;
    isMuted.value = false;
    holdRequested.value = false;
    controlMessage.value = '';
    showSummaryDrawer.value = true;
    hangupRequestKey = null;
  }

  async function saveSummary(summary?: { category: string; intent: string; notes: string }) {
    const callId = summaryCall.value?.callId;
    if (!callId) return false;
    const generation = summaryGeneration;
    try {
      await submitCallSummary(callId, summary || { category: '未分类', intent: 'UNASSESSED', notes: '' });
    }
    catch (error) {
      if (generation === summaryGeneration) toastError(error instanceof Error ? error.message : '小结提交失败');
      return false;
    }
    if (generation !== summaryGeneration) return false;
    summaryCalls.value = summaryCalls.value.filter(call => call.callId !== callId);
    showSummaryDrawer.value = false;
    return true;
  }

  async function toggleHold() {
    const callId = currentCall.value?.callId;
    const nextHeld = !holdRequested.value;
    const workNo = getWorkNo();
    if (!callId || !workNo || holdPending.value) return;
    holdPending.value = true;
    try {
      const result = await triggerHoldCall(workNo, callId, nextHeld);
      if (currentCall.value?.callId !== callId) return;
      holdRequested.value = nextHeld;
      controlMessage.value = result.message;
      toast(result.message, 'info');
    } catch (error) {
      toastError(error instanceof Error ? error.message : '保持请求失败');
    } finally { holdPending.value = false; }
  }

  function toggleMute() {
    const nextMuted = !isMuted.value;
    if (!sipWebRtcService.toggleMute(nextMuted)) {
      toastError('软电话媒体未连接，无法切换静音');
      return;
    }
    isMuted.value = nextMuted;
  }

  async function sendDtmf(digit: string) {
    const callId = currentCall.value?.callId;
    const workNo = getWorkNo();
    if (!callId || !workNo) return;
    try {
      await triggerDtmfCall(workNo, callId, digit);
    } catch (error) {
      toastError(error instanceof Error ? error.message : 'DTMF 请求失败');
    }
  }

  function observeReleased(callId?: string) {
    // Only a correlated participation-release fact may end the active media UI.
    if (callId) observeEnded(callId);
  }

  onScopeDispose(() => { stopCallTimer(); audioService.stopRingtone(); });

  return {
    callState,
    currentCall,
    durationSeconds,
    isHeld,
    holdRequested,
    holdPending,
    controlMessage,
    isMuted,
    showSummaryDrawer,
    summaryCall,
    summaryCalls,
    triggerIncoming,
    startOutbound,
    resetCall,
    handleIncomingSipSession,
    answerCall,
    observeAnswered,
    rejectCall,
    hangupCall,
    observeEnded,
    saveSummary,
    observeReleased,
    toggleHold,
    toggleMute,
    sendDtmf,
  };
});
