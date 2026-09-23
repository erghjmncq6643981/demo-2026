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
    toastError('当前通话尚未结束，不能切换接听终端');
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
  <div class="endpoint-selector">
    <div class="endpoint-heading">
      <span>接听终端</span>
      <button
        v-if="agentStore.endpointError"
        type="button"
        class="retry"
        :disabled="agentStore.endpointsLoading"
        @click="agentStore.loadEndpoints()"
      >
        重试
      </button>
    </div>
    <div class="select-wrap">
      <select
        :value="currentKey"
        :disabled="disabled"
        aria-label="切换接听终端"
        @change="change"
      >
        <option v-if="agentStore.endpointsLoading" :value="currentKey">正在加载终端…</option>
        <option v-else-if="!options.length" :value="currentKey">没有可用接听终端</option>
        <option
          v-for="option in options"
          :key="option.key"
          :value="option.key"
          :disabled="option.disabled"
        >
          {{ option.label }} · {{ option.detail }}
        </option>
      </select>
      <span v-if="agentStore.endpointSwitching" class="pending">切换中…</span>
    </div>
    <p v-if="callBlocksSwitch" class="hint">通话或话后整理期间不可切换</p>
    <p v-else-if="agentStore.endpointError" class="error" role="alert">
      {{ agentStore.endpointError }}
    </p>
  </div>
</template>

<style scoped>
.endpoint-selector {
  min-width: min(100%, 300px);
}
.endpoint-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
}
.select-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
select {
  width: 100%;
  max-width: 290px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  background: #fff;
  padding: 8px 32px 8px 10px;
  color: #1e293b;
  font-size: 12px;
  font-weight: 700;
}
select:disabled {
  background: #f8fafc;
  color: #64748b;
}
.pending,
.hint,
.error,
.retry {
  font-size: 11px;
}
.pending {
  color: #4f46e5;
  white-space: nowrap;
}
.hint,
.error {
  margin-top: 5px;
}
.hint {
  color: #64748b;
}
.error {
  color: #b42318;
}
.retry {
  color: #4f46e5;
}
</style>
