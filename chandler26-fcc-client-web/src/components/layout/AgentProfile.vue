<script setup lang="ts">
import EndpointSelector from '../../features/endpoint/components/EndpointSelector.vue';
import AgentPresenceControls from '../../features/identity/components/AgentPresenceControls.vue';
import CallStateBadge from '../../features/call/components/CallStateBadge.vue';
import { useAgentStore } from '../../stores/agentStore';

const agentStore = useAgentStore();
</script>

<template>
  <div class="px-4 sm:px-6 pt-3 pb-2 shrink-0">
    <div
      class="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/80 shadow-xs px-5 sm:px-6 py-3.5 sm:py-4 flex flex-wrap items-center justify-between gap-4"
    >
      <!-- 1. 姓名 & 2. 工号 -->
      <div class="flex items-center gap-3.5 shrink-0">
        <div
          class="w-11 h-11 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-600 text-white flex items-center justify-center font-extrabold text-base shadow-sm shrink-0 select-none"
        >
          {{ agentStore.agentName.substring(0, 1) || '坐' }}
        </div>
        <div class="flex items-center gap-2.5 flex-wrap">
          <span class="text-base sm:text-lg font-black text-slate-900 tracking-tight">
            {{ agentStore.agentName || '未命名坐席' }}
          </span>
          <span
            class="px-2.5 py-1 rounded-lg font-mono text-xs font-bold bg-slate-100 text-slate-700 border border-slate-200/80"
          >
            工号 {{ agentStore.workNo || '-' }}
          </span>
          <span
            v-if="agentStore.serviceGroup"
            class="px-2 py-0.5 rounded-md text-[11px] font-bold bg-slate-50 text-slate-500 border border-slate-200/60"
          >
            {{ agentStore.serviceGroup }}
          </span>
        </div>
      </div>

      <!-- 3. 接听终端 (标签在左侧) -> 4. 示闲 -> 5. 示忙 -> 6. 空闲 (通话状态，不显示说明) -->
      <div class="flex flex-wrap items-center gap-3 sm:gap-4">
        <!-- 3. 接听终端 (选择框左侧显示“接听终端”) -->
        <EndpointSelector layout="horizontal" />

        <!-- 细分隔线 -->
        <div class="h-5 w-[1px] bg-slate-200 hidden md:block"></div>

        <!-- 4. 示闲 & 5. 示忙 -->
        <AgentPresenceControls hide-label />

        <!-- 6. 空闲 (只显示通话状态，不显示说明) -->
        <CallStateBadge hide-label />
      </div>
    </div>
  </div>
</template>
