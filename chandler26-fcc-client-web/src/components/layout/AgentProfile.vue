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
          <div class="relative">
            <select
              :value="agentStore.endpoint"
              :disabled="endpointSwitchLocked"
              @change="handleEndpointSelect(($event.target as HTMLSelectElement).value as AnswerEndpointType)"
              class="bg-white border-2 border-slate-200 hover:border-brand-500 rounded-xl px-3 py-1.5 pr-8 text-sm font-extrabold text-slate-800 shadow-2xs focus:outline-none focus:border-brand-500 appearance-none cursor-pointer transition disabled:cursor-not-allowed disabled:opacity-50"
            >
              <option value="WEBRTC">💻 软话机 (WebRTC)</option>
              <option value="SIP">☎️ 实体话机 (SIP)</option>
              <option value="MOBILE">📱 随行手机 (Mobile)</option>
            </select>
            <span class="pointer-events-none absolute inset-y-0 right-2.5 flex items-center text-slate-400 text-xs">▼</span>
          </div>
        </div>

        <!-- 状态切换 (紧随接听方式右侧，保留 示闲 / 小休 / 示忙，彻底删除整理) -->
        <div class="flex items-center bg-slate-100/90 p-1 rounded-xl border border-slate-200 text-sm">
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
            @click="agentStore.setStatus('BUSY')"
            :class="[
              'flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-extrabold text-sm transition-all cursor-pointer',
              agentStore.status === 'BUSY' ? 'bg-white text-rose-700 shadow-2xs' : 'text-slate-600 hover:text-rose-600'
            ]"
          >
            <span class="w-2.5 h-2.5 rounded-full bg-rose-500"></span>
            <span>示忙</span>
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';
import type { AnswerEndpointType } from '../../types/telephony';
import { computed } from 'vue';
import { toast } from '../../utils/feedback';

const agentStore = useAgentStore();
const callStore = useCallStore();
const endpointSwitchLocked = computed(() => !['IDLE', 'ACW'].includes(callStore.callState));

async function handleEndpointSelect(type: AnswerEndpointType) {
  if (endpointSwitchLocked.value) {
    toast('通话进行中，不能切换接听方式');
    return;
  }
  let val = '';
  if (type === 'WEBRTC') val = agentStore.workNo;
  else if (type === 'SIP') val = agentStore.boundSipExtension || agentStore.extension;
  else val = agentStore.boundMobile;

  try {
    await agentStore.switchEndpoint(type, val);
  } catch (e) {
    console.warn('Endpoint switch failed:', e);
    toast('接听方式切换失败，请检查终端绑定');
  }
}
</script>
