<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';

const props = withDefaults(
  defineProps<{
    hideLabel?: boolean;
  }>(),
  {
    hideLabel: false,
  },
);

const agentStore = useAgentStore();
const callStore = useCallStore();
const blocked = computed(() =>
  callStore.callState !== 'IDLE'
  || ['CALLING', 'RINGING', 'ANSWERED', 'ACW'].includes(agentStore.workStatus)
  || agentStore.presencePending,
);
</script>

<template>
  <div class="flex items-center gap-2">
    <span v-if="!props.hideLabel" class="text-xs font-bold text-slate-600 select-none">工作状态</span>
    <div
      class="flex items-center gap-1 bg-slate-100/90 p-1 rounded-2xl border border-slate-200/80 shadow-2xs"
      role="group"
      aria-label="坐席工作状态"
    >
      <button
        type="button"
        :disabled="blocked"
        :aria-pressed="agentStore.loginStatus === 'LOGIN'"
        class="h-8 px-3.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed select-none"
        :class="agentStore.loginStatus === 'LOGIN' ? 'bg-white text-emerald-700 shadow-xs font-black' : 'text-slate-500 hover:text-slate-900'"
        @click="agentStore.setLoginStatus('LOGIN')"
      >
        <span
          class="w-2 h-2 rounded-full transition-colors"
          :class="agentStore.loginStatus === 'LOGIN' ? 'bg-emerald-500 ring-2 ring-emerald-200' : 'bg-slate-300'"
        />
        <span>示闲</span>
      </button>
      <button
        type="button"
        :disabled="blocked"
        :aria-pressed="agentStore.loginStatus === 'LOGIN_BUSY'"
        class="h-8 px-3.5 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed select-none"
        :class="agentStore.loginStatus === 'LOGIN_BUSY' ? 'bg-white text-amber-700 shadow-xs font-black' : 'text-slate-500 hover:text-slate-900'"
        @click="agentStore.setLoginStatus('LOGIN_BUSY')"
      >
        <span
          class="w-2 h-2 rounded-full transition-colors"
          :class="agentStore.loginStatus === 'LOGIN_BUSY' ? 'bg-amber-500 ring-2 ring-amber-200' : 'bg-slate-300'"
        />
        <span>示忙</span>
      </button>
    </div>
  </div>
</template>
