<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';
import { callStateLabel } from '../model/callPresentation';

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
const label = computed(() => callStateLabel(callStore.callState, agentStore.workStatus));

const badgeStyle = computed(() => {
  const state = callStore.callState;
  const runtime = agentStore.workStatus;
  const effective = state === 'IDLE' ? runtime : state;

  if (effective === 'CONNECTED' || effective === 'ANSWERED') {
    return {
      badge: 'bg-emerald-50 text-emerald-800 border-emerald-300 font-extrabold',
      dot: 'bg-emerald-500 animate-pulse',
    };
  }
  if (effective === 'RINGING') {
    return {
      badge: 'bg-amber-50 text-amber-800 border-amber-300 animate-pulse font-extrabold',
      dot: 'bg-amber-500 animate-ping',
    };
  }
  if (effective === 'CALLING') {
    return {
      badge: 'bg-blue-50 text-blue-800 border-blue-300 font-extrabold',
      dot: 'bg-blue-500 animate-pulse',
    };
  }
  if (effective === 'ENDING') {
    return {
      badge: 'bg-slate-100 text-slate-600 border-slate-200',
      dot: 'bg-slate-400',
    };
  }
  // IDLE / READY / BUSY / UNREADY / REST -> 空闲态
  return {
    badge: 'bg-slate-50 text-slate-700 border-slate-200/90 font-bold',
    dot: 'bg-slate-400',
  };
});
</script>

<template>
  <div class="flex items-center gap-2">
    <span v-if="!props.hideLabel" class="text-xs font-bold text-slate-600 select-none">通话状态</span>
    <button
      v-if="callStore.callState === 'ACW'"
      type="button"
      class="h-8 px-3.5 rounded-xl border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-brand-700 text-xs font-bold font-mono inline-flex items-center gap-1.5 shadow-2xs transition cursor-pointer select-none"
      title="点击继续填写话后小结"
      @click="callStore.showAcwDrawer = true"
    >
      <span class="w-2 h-2 rounded-full bg-brand-500 animate-pulse" />
      <span>{{ label }} · 继续填写</span>
    </button>
    <div
      v-else
      class="h-8 px-3.5 rounded-xl border text-xs font-mono inline-flex items-center gap-1.5 shadow-2xs select-none"
      :class="badgeStyle.badge"
    >
      <span class="w-2 h-2 rounded-full" :class="badgeStyle.dot" />
      <span>{{ label }}</span>
    </div>
  </div>
</template>
