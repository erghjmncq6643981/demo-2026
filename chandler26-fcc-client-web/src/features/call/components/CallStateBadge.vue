<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';
import { callStateLabel, callStateTone } from '../model/callPresentation';

const agentStore = useAgentStore();
const callStore = useCallStore();
const label = computed(() => callStateLabel(callStore.callState, agentStore.workStatus));
const tone = computed(() => callStateTone(callStore.callState, agentStore.workStatus));
</script>

<template>
  <div class="call-state">
    <span class="caption">通话状态</span>
    <button
      v-if="callStore.callState === 'ACW'"
      type="button"
      class="badge warning actionable"
      @click="callStore.showAcwDrawer = true"
    >
      {{ label }} · 继续填写
    </button>
    <span v-else class="badge" :class="tone">{{ label }}</span>
  </div>
</template>

<style scoped>
.call-state { display: grid; gap: 5px; }
.caption { color: #475569; font-size: 11px; font-weight: 800; }
.badge { width: fit-content; border: 1px solid #cbd5e1; border-radius: 999px; padding: 6px 10px; background: #f8fafc; color: #475569; font-size: 12px; font-weight: 800; }
.badge.danger { border-color: #fecaca; background: #fff1f2; color: #be123c; }
.badge.warning { border-color: #c7d2fe; background: #eef2ff; color: #4338ca; }
.actionable:hover { border-color: #818cf8; }
</style>
