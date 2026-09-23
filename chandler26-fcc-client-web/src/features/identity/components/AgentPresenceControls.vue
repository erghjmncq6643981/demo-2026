<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';

const agentStore = useAgentStore();
const callStore = useCallStore();
const blocked = computed(() =>
  callStore.callState !== 'IDLE'
  || ['CALLING', 'RINGING', 'ANSWERED', 'ACW'].includes(agentStore.workStatus)
  || agentStore.presencePending,
);
</script>

<template>
  <div class="presence">
    <span class="label">工作状态</span>
    <div class="controls" role="group" aria-label="坐席工作状态">
      <button
        type="button"
        :disabled="blocked"
        :aria-pressed="agentStore.loginStatus === 'LOGIN'"
        :class="{ active: agentStore.loginStatus === 'LOGIN', ready: true }"
        @click="agentStore.setLoginStatus('LOGIN')"
      >
        <span class="dot" />示闲
      </button>
      <button
        type="button"
        :disabled="blocked"
        :aria-pressed="agentStore.loginStatus === 'LOGIN_BUSY'"
        :class="{ active: agentStore.loginStatus === 'LOGIN_BUSY', busy: true }"
        @click="agentStore.setLoginStatus('LOGIN_BUSY')"
      >
        <span class="dot" />示忙
      </button>
    </div>
  </div>
</template>

<style scoped>
.presence { display: grid; gap: 5px; }
.label { color: #475569; font-size: 11px; font-weight: 800; }
.controls { display: flex; gap: 4px; padding: 3px; border: 1px solid #e2e8f0; border-radius: 10px; background: #f1f5f9; }
button { display: flex; align-items: center; gap: 6px; border-radius: 7px; padding: 6px 11px; color: #64748b; font-size: 12px; font-weight: 800; }
button.active { background: #fff; box-shadow: 0 1px 2px rgb(15 23 42 / 8%); }
button.ready.active { color: #047857; }
button.busy.active { color: #b45309; }
button:disabled { cursor: not-allowed; opacity: 0.58; }
.dot { width: 8px; height: 8px; border-radius: 999px; background: currentColor; }
</style>
