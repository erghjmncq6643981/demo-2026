<template>
  <section class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-hidden">
    <header class="pb-4 border-b border-slate-100">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="text-base font-black text-slate-900">我的坐席状态</h2>
          <p class="text-xs text-slate-400 mt-1">当前页面只展示已认证坐席本人状态，不维护固定人员名单。</p>
        </div>
        <span class="px-2.5 py-1 rounded-full text-xs font-bold" :class="statusClass">
          {{ statusLabel }}
        </span>
      </div>
    </header>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 py-6">
      <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <p class="text-xs text-slate-400">当前坐席</p>
        <p class="mt-2 text-lg font-black text-slate-900">{{ agentStore.agentName || '未命名坐席' }}</p>
        <p class="mt-1 text-xs font-mono text-slate-500">工号 {{ agentStore.workNo || '-' }}</p>
        <p class="mt-3 text-xs text-slate-500">角色：{{ agentStore.isSupervisor ? '班长主管' : '普通坐席' }}</p>
      </div>

      <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <p class="text-xs text-slate-400">当前接听终端</p>
        <p class="mt-2 text-lg font-black text-slate-900">{{ endpointLabel }}</p>
        <p class="mt-1 text-xs font-mono text-slate-500">{{ agentStore.extension || '未分配终端' }}</p>
        <p class="mt-3 text-xs text-slate-500">终端切换请在管理端完成，通话或话后整理期间不可切换。</p>
      </div>
    </div>

    <div class="rounded-2xl border border-slate-200 p-4">
      <div class="flex items-center justify-between gap-4">
        <div>
          <p class="text-xs text-slate-400">当前业务通话</p>
          <p class="mt-1 text-sm font-bold text-slate-900">{{ callDescription }}</p>
        </div>
        <p v-if="callStore.currentCall?.callId" class="text-xs font-mono text-slate-400">
          call_id {{ callStore.currentCall.callId }}
        </p>
      </div>
    </div>

    <div class="mt-auto pt-6 text-xs text-slate-400">
      坐席列表、团队状态和班长干预将在服务端提供正式权限与实时接口后开放；当前不使用静态人员数据或模拟数据。
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';

const agentStore = useAgentStore();
const callStore = useCallStore();

const statusLabel = computed(() => {
  if (callStore.callState === 'RINGING') return '振铃中';
  if (callStore.callState === 'CALLING') return '呼叫中';
  if (callStore.callState === 'CONNECTED') return '通话中';
  if (callStore.callState === 'ENDING') return '结束中';
  if (callStore.callState === 'ACW') return '话后整理';
  if (agentStore.status === 'READY') return '示闲就绪';
  if (agentStore.status === 'BUSY') return '通话占用';
  return '小休';
});

const statusClass = computed(() => {
  if (callStore.callState === 'CONNECTED' || agentStore.status === 'BUSY') {
    return 'bg-rose-50 text-rose-700 border border-rose-200';
  }
  if (callStore.callState === 'ACW' || agentStore.status === 'ACW') {
    return 'bg-indigo-50 text-indigo-700 border border-indigo-200';
  }
  if (agentStore.status === 'READY') {
    return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
  }
  return 'bg-amber-50 text-amber-700 border border-amber-200';
});

const endpointLabel = computed(() => {
  if (agentStore.endpoint === 'WEBRTC') return 'WebRTC 软电话';
  if (agentStore.endpoint === 'SIP') return '物理 SIP 话机';
  return '手机接听（未实现）';
});

const callDescription = computed(() => {
  const call = callStore.currentCall;
  if (!call) return '当前没有进行中的业务通话';
  return `${call.direction === 'OUTBOUND' ? '外呼' : '呼入'} · ${call.callerNumber || '号码未提供'}`;
});
</script>
