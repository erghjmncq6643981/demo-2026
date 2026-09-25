<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';
import { toastError } from '../../../utils/feedback';
import {
  buildEndpointOptions,
  endpointKey,
  parseEndpointKey,
} from '../model/endpointSelection';

const props = withDefaults(
  defineProps<{
    layout?: 'horizontal' | 'vertical';
  }>(),
  {
    layout: 'horizontal',
  },
);

const agentStore = useAgentStore();
const callStore = useCallStore();

const options = computed(() => buildEndpointOptions({
  workNo: agentStore.workNo,
  webrtcWorkNo: agentStore.webrtcWorkNo,
  sipExtensions: agentStore.availableSipExtensions,
  mobilePhone: agentStore.boundMobile,
}));
const currentKey = computed(() => endpointKey(agentStore.endpoint, agentStore.extension));
const callBlocksSwitch = computed(() => callStore.callState !== 'IDLE');
const disabled = computed(() =>
  agentStore.endpointsLoading || agentStore.endpointSwitching || callBlocksSwitch.value,
);

async function change(event: Event) {
  const selected = parseEndpointKey((event.target as HTMLSelectElement).value);
  if (
    !selected ||
    selected.type === 'MOBILE' ||
    endpointKey(selected.type, selected.value) === currentKey.value
  ) return;
  if (callBlocksSwitch.value) {
    toastError('当前通话尚未结束，不能切换接听方式');
    return;
  }
  try {
    await agentStore.switchEndpoint(selected.type, selected.value);
  } catch {
    // Store owns the visible error state and retry behavior.
  }
}
</script>

<template>
  <!-- Horizontal layout (default): label on the left -->
  <div v-if="props.layout === 'horizontal'" class="flex items-center gap-2.5">
    <span class="text-xs font-bold text-slate-600 shrink-0 select-none">接听方式</span>
    <div class="relative flex items-center">
      <select
        :value="currentKey"
        :disabled="disabled"
        aria-label="切换接听方式"
        class="h-9 bg-slate-50 hover:bg-slate-100/80 focus:bg-white border border-slate-200/90 rounded-xl px-3 py-1.5 text-xs font-bold text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition shadow-2xs cursor-pointer disabled:bg-slate-100 disabled:text-slate-400 disabled:cursor-not-allowed max-w-[280px]"
        @change="change"
      >
        <option v-if="agentStore.endpointsLoading" :value="currentKey">正在加载接听方式…</option>
        <option v-else-if="!options.length" :value="currentKey">没有可用接听方式</option>
        <option
          v-for="option in options"
          :key="option.key"
          :value="option.key"
          :disabled="option.disabled"
        >
          {{ option.label }} · {{ option.detail }}
        </option>
      </select>
      <span v-if="agentStore.endpointSwitching" class="ml-2 text-xs font-bold text-brand-600 animate-pulse whitespace-nowrap">切换中…</span>
      <button
        v-if="agentStore.endpointError"
        type="button"
        class="ml-2 text-xs font-bold text-rose-600 underline hover:text-rose-700 cursor-pointer whitespace-nowrap"
        :disabled="agentStore.endpointsLoading"
        @click="agentStore.loadEndpoints()"
      >
        重试
      </button>
    </div>
  </div>

  <!-- Vertical layout: heading on top -->
  <div v-else class="min-w-full">
    <div class="flex items-center justify-between mb-1.5 text-slate-600 text-xs font-extrabold">
      <span>接听方式</span>
      <button
        v-if="agentStore.endpointError"
        type="button"
        class="text-brand-600 text-xs font-bold underline"
        :disabled="agentStore.endpointsLoading"
        @click="agentStore.loadEndpoints()"
      >
        重试
      </button>
    </div>
    <div class="flex items-center gap-2">
      <select
        :value="currentKey"
        :disabled="disabled"
        aria-label="切换接听方式"
        class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-bold text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500"
        @change="change"
      >
        <option v-if="agentStore.endpointsLoading" :value="currentKey">正在加载接听方式…</option>
        <option v-else-if="!options.length" :value="currentKey">没有可用接听方式</option>
        <option
          v-for="option in options"
          :key="option.key"
          :value="option.key"
          :disabled="option.disabled"
        >
          {{ option.label }} · {{ option.detail }}
        </option>
      </select>
      <span v-if="agentStore.endpointSwitching" class="text-xs font-bold text-brand-600 animate-pulse">切换中…</span>
    </div>
    <p v-if="callBlocksSwitch" class="mt-1 text-[11px] text-slate-400">通话或话后整理期间不可切换</p>
    <p v-else-if="agentStore.endpointError" class="mt-1 text-[11px] text-rose-600" role="alert">
      {{ agentStore.endpointError }}
    </p>
  </div>
</template>
