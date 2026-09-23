<template>
  <div class="px-4 sm:px-6 pt-3 pb-2 shrink-0">
    <div class="bg-white p-3.5 sm:p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-wrap items-center justify-between gap-3.5">
      
      <!-- 左侧: 坐席身份与极简接听方式 -->
      <div class="flex flex-wrap items-center gap-3 sm:gap-4">
        <!-- 坐席头像首字 -->
        <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-600 text-white font-extrabold flex items-center justify-center text-base shadow-xs">
          {{ agentStore.agentName.substring(0, 1) }}
        </div>

        <!-- 姓名、工号、组别 -->
        <div class="flex flex-wrap items-center gap-2 sm:gap-3">
          <span class="text-base sm:text-lg font-extrabold text-slate-900 tracking-tight">
            {{ agentStore.agentName }}
          </span>
          <span class="text-xs font-mono font-bold text-slate-700 bg-slate-100 px-2 py-0.5 rounded-md border border-slate-200">
            工号 {{ agentStore.workNo }}
          </span>
          <span class="text-xs text-slate-400 hidden md:inline">|</span>
          <span class="text-xs text-slate-600 hidden md:inline">
            {{ agentStore.serviceGroup }}
          </span>
        </div>

        <div class="h-4 w-[1px] bg-slate-200 hidden lg:block"></div>

        <!-- 显眼接听方式下拉框与紧随其右的状态切换 -->
        <div class="flex items-center gap-2">
          <label class="text-sm font-extrabold text-slate-800 shrink-0">接听方式:</label>
          <span class="text-sm font-bold text-slate-700">
            {{ endpointLabel }} · {{ endpointValue }}
          </span>
        </div>

        <!-- 状态切换 (紧随接听方式右侧，保留 示闲 / 小休 / 示忙，彻底删除整理) -->
        <div class="flex items-center bg-slate-100/90 p-1 rounded-xl border border-slate-200 text-sm">
          <button v-if="callStore.callState === 'ACW'" class="px-3 py-1.5 font-bold text-indigo-700" @click="callStore.showAcwDrawer = true">继续话后整理</button>
          <button
            @click="agentStore.setStatus('READY')"
            :class="[
              'flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-extrabold text-sm transition-all cursor-pointer',
              agentStore.status === 'READY' ? 'bg-white text-emerald-700 shadow-2xs' : 'text-slate-600 hover:text-emerald-600'
            ]"
          >
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            <span>示闲 (Ready)</span>
          </button>

          <button
            @click="agentStore.setStatus('REST')"
            :class="[
              'flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-extrabold text-sm transition-all cursor-pointer',
              agentStore.status === 'REST' ? 'bg-white text-amber-700 shadow-2xs' : 'text-slate-600 hover:text-amber-600'
            ]"
          >
            <span class="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
            <span>小休</span>
          </button>

          <button
            disabled
            title="忙碌状态由服务端通话分配维护"
            :class="[
              'flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-extrabold text-sm transition-all cursor-pointer',
              agentStore.status === 'BUSY' ? 'bg-white text-rose-700 shadow-2xs' : 'text-slate-600 hover:text-rose-600'
            ]"
          >
            <span class="w-2.5 h-2.5 rounded-full bg-rose-500"></span>
            <span>通话忙碌（自动）</span>
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';

const agentStore = useAgentStore();
const callStore = useCallStore();

const endpointLabel = computed(() => {
  if (agentStore.endpoint === 'WEBRTC') return 'WebRTC 软话机';
  if (agentStore.endpoint === 'SIP') return 'SIP 话机';
  return '手机';
});

const endpointValue = computed(() => {
  if (agentStore.endpoint === 'MOBILE') return agentStore.boundMobile || '尚未绑定';
  return agentStore.boundSipExtension || agentStore.extension || '尚未绑定';
});
</script>
