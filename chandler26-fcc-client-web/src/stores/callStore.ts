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
    if (callState.value === 'RINGING' && currentCall.value) {
      currentCall.value = { ...currentCall.value, ...payload };
      if (!lifecycle.value.callId || lifecycle.value.callId.startsWith('sip-')) {
        lifecycle.value = { ...lifecycle.value, callId: payload.callId };
      }
      return;
    }
    if (!applyLifecycle({ type: 'INCOMING', callId: payload.callId })) return;
    currentCall.value = payload;
    audioService.startRingtone();
  }

  function startOutbound(callId?: string) {
    applyLifecycle({ type: 'OUTBOUND_STARTED', callId });
  }

  function answerCall() {
    audioService.stopRingtone();
    sipWebRtcService.answer();
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
      if (!callId.startsWith('sip-')) {
        const workNo = getWorkNo();
        if (!workNo) throw new Error('登录身份不可用');
        const result = await triggerHangupCall(workNo, callId, reason || 'NORMAL_CLEARING');
        if (currentCall.value?.callId !== callId) return;
        controlMessage.value = result.message;
      }
      audioService.stopRingtone();
      applyLifecycle({ type: 'HANGUP_REQUESTED', callId });
      sipWebRtcService.hangup();
    } catch (error) {
      controlMessage.value = error instanceof Error ? error.message : '挂机请求失败';
      toastError(controlMessage.value);
    } finally { hangupRequestKey = null; }
  }

  function observeEnded(callId?: string) {
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
    isMuted.value = !isMuted.value;
    sipWebRtcService.toggleMute(isMuted.value);
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
