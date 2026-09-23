<template>
  <footer class="min-h-9 bg-white border-t border-slate-100 px-4 sm:px-8 py-2 flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-400 select-none shrink-0">
    <div class="flex flex-wrap items-center gap-x-5 gap-y-1">
      <span>当前终端: <strong class="text-slate-800 font-mono">{{ endpointSummary }}</strong></span>
      <span v-if="agentStore.serviceGroup">服务组: <strong class="text-slate-800">{{ agentStore.serviceGroup }}</strong></span>
      <span>音频设备: <strong class="text-slate-700">{{ audioSummary }}</strong></span>
    </div>

    <div class="flex flex-wrap items-center gap-x-4 gap-y-1 font-mono">
      <span>当前页呼入: <strong class="text-slate-700">{{ cdrStore.pageInboundCount }} 通</strong></span>
      <span>当前页外呼: <strong class="text-slate-700">{{ cdrStore.pageOutboundCount }} 通</strong></span>
      <span :class="['font-bold flex items-center gap-1', signalingClass]">
        <span :class="['w-1.5 h-1.5 rounded-full', signalingDotClass]"></span>
        {{ signalingLabel }}
      </span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { useCdrStore } from '../../stores/cdrStore';
import { sipWebRtcService } from '../../services/sipWebRtcService';
import { wsService } from '../../services/websocketService';

const agentStore = useAgentStore();
const cdrStore = useCdrStore();

const endpointSummary = computed(() => {
  if (agentStore.endpoint === 'MOBILE') return agentStore.boundMobile || '手机未绑定';
  const value = agentStore.boundSipExtension || agentStore.extension || '未绑定';
  return `${agentStore.endpoint === 'WEBRTC' ? 'WebRTC' : 'SIP'} · ${value}`;
});

const audioSummary = computed(() => agentStore.endpoint === 'WEBRTC'
  ? `${sipWebRtcService.audio.audioInputLabel.value} / ${sipWebRtcService.audio.audioOutputLabel.value}`
  : '由外部终端管理');

const signalingLabel = computed(() => {
  switch (wsService.connectionState.value) {
    case 'CONNECTED': return '业务信令在线';
    case 'CONNECTING': return '业务信令连接中';
    case 'RECONNECTING': return '业务信令重连中';
    default: return '业务信令离线';
  }
});

const signalingClass = computed(() => wsService.connectionState.value === 'CONNECTED'
  ? 'text-emerald-600'
  : wsService.connectionState.value === 'DISCONNECTED' ? 'text-rose-600' : 'text-amber-600');

const signalingDotClass = computed(() => wsService.connectionState.value === 'CONNECTED'
  ? 'bg-emerald-500'
  : wsService.connectionState.value === 'DISCONNECTED' ? 'bg-rose-500' : 'bg-amber-500');
</script>
