import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { IncomingScreenPopPayload } from '../types/telephony';
import { audioService } from '../services/audioService';
import { toast, toastError } from '../utils/feedback';
import { sipWebRtcService } from '../services/sipWebRtcService';
import { triggerHangupCall, triggerHoldCall, triggerDtmfCall } from '../api/telephonyApi';
import { telephonyApi } from '../api/apiClient';
import { useAgentStore } from './agentStore';
import {
  initialCallLifecycle,
  reduceCallLifecycle,
  type CallLifecycleEvent,
} from '../features/call/model/callStateMachine';

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
  const showAcwDrawer = ref(false);

  let callTimerInterval: number | null = null;
  let hangupRequestKey: string | null = null;

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
    sipWebRtcService.bindBusinessCall(payload.callId);
    if (currentCall.value?.callId === payload.callId && callState.value !== 'ACW') {
      currentCall.value = { ...currentCall.value, ...payload };
      return;
    }
    if (callState.value !== 'IDLE') return;
    const event: CallLifecycleEvent = payload.direction === 'OUTBOUND'
      ? { type: 'OUTBOUND_STARTED', callId: payload.callId }
      : { type: 'INCOMING', callId: payload.callId };
    if (!applyLifecycle(event)) return;
    currentCall.value = payload;
    controlMessage.value = '';
    isMuted.value = false;
    if (event.type === 'INCOMING') audioService.startRingtone();
  }

  function startOutbound(callId: string, calleeNumber?: string) {
    sipWebRtcService.bindBusinessCall(callId);
    if (currentCall.value?.callId === callId && callState.value === 'CALLING') {
      currentCall.value = {
        ...currentCall.value,
        direction: 'OUTBOUND',
        callerNumber: currentCall.value.callerNumber || calleeNumber,
      };
      return;
    }
    if (!applyLifecycle({ type: 'OUTBOUND_STARTED', callId })) return;
    currentCall.value = {
      callId,
      direction: 'OUTBOUND',
      callerNumber: calleeNumber,
    };
    controlMessage.value = '';
    isMuted.value = false;
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
    if (callId) sipWebRtcService.releasePendingBusinessCall(callId);
    if (!applyLifecycle({ type: 'ENDED', callId })) return;
    audioService.stopRingtone();
    stopCallTimer();
    showAcwDrawer.value = true;
    hangupRequestKey = null;
  }

  async function closeAcw(summary?: { category: string; intent: string; notes: string }) {
    const callId = currentCall.value?.callId;
    if (!callId) return;
    try {
      await telephonyApi.post(`/calls/${encodeURIComponent(callId)}/summary`, summary || { category: '未分类', intent: 'UNASSESSED', notes: '' });
    }
    catch (error) { toastError(error instanceof Error ? error.message : '整理提交失败'); return; }
    await useAgentStore().refreshStatus();
    if (!applyLifecycle({ type: 'ACW_COMPLETED' })) return;
    showAcwDrawer.value = false;
    currentCall.value = null;
    durationSeconds.value = 0;
    isHeld.value = false;
    isMuted.value = false;
    holdRequested.value = false;
    controlMessage.value = '';
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

  return {
    callState,
    currentCall,
    durationSeconds,
    isHeld,
    holdRequested,
    holdPending,
    controlMessage,
    isMuted,
    showAcwDrawer,
    triggerIncoming,
    startOutbound,
    answerCall,
    observeAnswered,
    rejectCall,
    hangupCall,
    observeEnded,
    closeAcw,
    toggleHold,
    toggleMute,
    sendDtmf,
  };
});
