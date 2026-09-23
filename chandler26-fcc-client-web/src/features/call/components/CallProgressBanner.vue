<template>
  <div
    v-if="callStore.callState === 'CALLING'"
    class="mx-4 sm:mx-6 mb-2 rounded-2xl border border-indigo-200 bg-indigo-50 px-4 py-3 flex flex-wrap items-center justify-between gap-3"
    role="status"
  >
    <div class="min-w-0">
      <div class="text-sm font-extrabold text-indigo-900">外呼指令已受理，等待话务事件确认</div>
      <div class="mt-1 text-xs text-indigo-700 flex flex-wrap gap-x-3 gap-y-1">
        <span class="font-mono break-all">被叫: {{ callStore.currentCall?.callerNumber || '未提供' }}</span>
        <span>接听终端: {{ endpointLabel }}</span>
        <span v-if="callStore.controlMessage">{{ callStore.controlMessage }}</span>
      </div>
    </div>
    <button
      @click="callStore.hangupCall('USER_CANCEL')"
      class="rounded-xl border border-rose-200 bg-white px-4 py-2 text-xs font-bold text-rose-600 hover:bg-rose-50"
    >
      取消外呼
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';

const agentStore = useAgentStore();
const callStore = useCallStore();

const endpointLabel = computed(() => {
  if (agentStore.endpoint === 'WEBRTC') return 'WebRTC 软话机';
  if (agentStore.endpoint === 'SIP') return 'SIP 话机';
  return '手机';
});
</script>
