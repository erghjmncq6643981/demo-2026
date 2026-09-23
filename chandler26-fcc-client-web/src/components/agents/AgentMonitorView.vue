<script setup lang="ts">
import { computed } from 'vue';
import EndpointSelector from '../../features/endpoint/components/EndpointSelector.vue';
import { callStateLabel, callStateTone } from '../../features/call/model/callPresentation';
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';

const agentStore = useAgentStore();
const callStore = useCallStore();

const presenceLabel = computed(() =>
  agentStore.loginStatus === 'LOGIN'
    ? '示闲 · 可接来电和外呼'
    : agentStore.loginStatus === 'LOGIN_BUSY'
      ? '示忙 · 仅允许主动外呼'
      : '已退出 · 不参与呼叫',
);
const callLabel = computed(() => callStateLabel(callStore.callState, agentStore.workStatus));
const callTone = computed(() => callStateTone(callStore.callState, agentStore.workStatus));
const callDescription = computed(() => {
  const call = callStore.currentCall;
  if (!call) return '当前没有本机已恢复的业务通话';
  return `${call.direction === 'OUTBOUND' ? '外呼' : '呼入'} · ${call.callerNumber || '号码未提供'}`;
});
</script>

<template>
  <section class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-hidden">
    <header class="pb-4 border-b border-slate-100">
      <h2 class="text-base font-black text-slate-900">我的坐席状态</h2>
      <p class="text-xs text-slate-400 mt-1">工作状态决定是否参与分配，通话状态由真实话务事件推进，两者互不替代。</p>
    </header>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 py-6">
      <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <p class="text-xs text-slate-400">当前坐席</p>
        <p class="mt-2 text-lg font-black text-slate-900">{{ agentStore.agentName || '未命名坐席' }}</p>
        <p class="mt-1 text-xs font-mono text-slate-500">工号 {{ agentStore.workNo || '-' }}</p>
        <p class="mt-3 text-xs text-slate-500">角色：{{ agentStore.isSupervisor ? '班长主管' : '普通坐席' }}</p>
      </div>

      <div class="rounded-2xl border border-slate-200 bg-white p-4">
        <p class="text-xs text-slate-400">工作状态</p>
        <p class="mt-2 text-lg font-black" :class="agentStore.loginStatus === 'LOGIN' ? 'text-emerald-700' : 'text-amber-700'">
          {{ presenceLabel }}
        </p>
        <p class="mt-3 text-xs text-slate-500">示忙只屏蔽呼入分配，坐席仍可专注进行主动外呼。</p>
      </div>

      <div class="rounded-2xl border border-slate-200 bg-white p-4">
        <p class="text-xs text-slate-400">通话状态</p>
        <p
          class="mt-2 text-lg font-black"
          :class="callTone === 'danger' ? 'text-rose-700' : callTone === 'warning' ? 'text-indigo-700' : 'text-slate-700'"
        >
          {{ callLabel }}
        </p>
        <p class="mt-3 text-xs text-slate-500">振铃、呼叫、通话、结束和话后整理均由通话状态机维护。</p>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[minmax(280px,360px)_1fr] gap-4">
      <div class="rounded-2xl border border-slate-200 p-4">
        <EndpointSelector />
      </div>
      <div class="rounded-2xl border border-slate-200 p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="text-xs text-slate-400">当前业务通话</p>
            <p class="mt-1 text-sm font-bold text-slate-900">{{ callDescription }}</p>
          </div>
          <p v-if="callStore.currentCall?.callId" class="text-xs font-mono text-slate-400 break-all">
            call_id {{ callStore.currentCall.callId }}
          </p>
        </div>
      </div>
    </div>

    <div class="mt-auto pt-6 text-xs text-slate-400">
      团队状态和班长干预将在服务端提供正式权限与实时接口后开放；当前页面不展示静态人员或模拟在线数据。
    </div>
  </section>
</template>
