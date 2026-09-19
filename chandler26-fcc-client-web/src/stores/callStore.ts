import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { CallState, IncomingScreenPopPayload } from '../types/telephony';
import { audioService } from '../services/audioService';
import { sipWebRtcService } from '../services/sipWebRtcService';
import { triggerHangupCall, triggerHoldCall, triggerDtmfCall } from '../api/telephonyApi';

export const useCallStore = defineStore('call', () => {
  const callState = ref<CallState>('IDLE');
  const currentCall = ref<IncomingScreenPopPayload | null>(null);
  const durationSeconds = ref(0);
  const isHeld = ref(false);
  const isMuted = ref(false);
  const showAcwDrawer = ref(false);

  let callTimerInterval: number | null = null;

  function getWorkNo(): string {
    return localStorage.getItem('fcc_agent_workno') || '901001';
  }

  function triggerIncoming(payload: IncomingScreenPopPayload) {
    currentCall.value = payload;
    callState.value = 'RINGING';
    audioService.startRingtone();
  }

  function answerCall() {
    audioService.stopRingtone();
    // 触发真实 WebRTC 应答以打通双向语音媒体流
    sipWebRtcService.answer();
    callState.value = 'CONNECTED';
    durationSeconds.value = 0;
    isHeld.value = false;

    if (callTimerInterval !== null) clearInterval(callTimerInterval);
    callTimerInterval = window.setInterval(() => {
      durationSeconds.value++;
    }, 1000);
  }

  async function rejectCall() {
    audioService.stopRingtone();
    sipWebRtcService.hangup();
    const callId = currentCall.value?.callId;
    callState.value = 'IDLE';
    currentCall.value = null;

    if (callId) {
      try {
        await triggerHangupCall(getWorkNo(), callId, 'USER_REJECT');
      } catch (e) {
        console.warn('Reject call api failed:', e);
      }
    }
  }

  async function hangupCall(reason?: string) {
    audioService.stopRingtone();
    sipWebRtcService.hangup();
    if (callTimerInterval !== null) {
      clearInterval(callTimerInterval);
      callTimerInterval = null;
    }
    const callId = currentCall.value?.callId;
    callState.value = 'ACW';
    showAcwDrawer.value = true;

    if (callId) {
      try {
        await triggerHangupCall(getWorkNo(), callId, reason || 'NORMAL_CLEARING');
      } catch (e) {
        console.warn('Hangup call api failed:', e);
      }
    }
  }

  function closeAcw() {
    showAcwDrawer.value = false;
    callState.value = 'IDLE';
    currentCall.value = null;
    durationSeconds.value = 0;
  }

  async function toggleHold() {
    isHeld.value = !isHeld.value;
    const callId = currentCall.value?.callId;
    if (callId) {
      try {
        await triggerHoldCall(getWorkNo(), callId, isHeld.value);
      } catch (e) {
        console.warn('Toggle hold api failed:', e);
      }
    }
  }

  function toggleMute() {
    isMuted.value = !isMuted.value;
    sipWebRtcService.toggleMute(isMuted.value);
  }

  async function sendDtmf(digit: string) {
    sipWebRtcService.sendDtmf(digit);
    const callId = currentCall.value?.callId;
    if (callId) {
      try {
        await triggerDtmfCall(getWorkNo(), callId, digit);
      } catch (e) {
        console.warn('Send DTMF api failed:', e);
      }
    }
  }

  return {
    callState,
    currentCall,
    durationSeconds,
    isHeld,
    isMuted,
    showAcwDrawer,
    triggerIncoming,
    answerCall,
    rejectCall,
    hangupCall,
    closeAcw,
    toggleHold,
    toggleMute,
    sendDtmf,
  };
});
