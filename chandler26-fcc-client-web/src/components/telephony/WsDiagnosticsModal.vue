<template>
  <div
    v-if="visible"
    class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4 animate-in fade-in duration-200"
  >
    <div class="w-full max-w-md bg-white rounded-3xl shadow-2xl p-6 border border-slate-100 animate-in zoom-in-95 duration-200">
      <div class="flex items-center justify-between pb-3.5 border-b border-slate-100">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-2xl bg-indigo-50 text-brand-600 flex items-center justify-center font-bold text-lg">
            🔌
          </div>
          <div>
            <h3 class="font-extrabold text-sm text-slate-900">WebSocket 话务信道态势</h3>
            <p class="text-[11px] text-slate-400">实时双向信令 · 坐席工号 {{ agentStore.workNo }} ({{ agentStore.agentName }})</p>
          </div>
        </div>
        <button
          @click="$emit('close')"
          class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-sm font-bold"
        >
          ✕
        </button>
      </div>

      <div class="my-4 space-y-3 text-xs">
        <div class="flex justify-between items-center p-3 bg-slate-50 rounded-2xl">
          <span class="text-slate-500">信道状态:</span>
          <span
            :class="[
              'font-extrabold flex items-center gap-1.5',
              wsConnected ? 'text-emerald-600' : 'text-rose-500'
            ]"
          >
            <span :class="['w-2 h-2 rounded-full', wsConnected ? 'bg-emerald-500 status-pulse-ready' : 'bg-rose-500']"></span>
            {{ wsConnected ? 'CONNECTED (已连通)' : 'DISCONNECTED (未连接)' }}
          </span>
        </div>

        <div class="p-3 bg-slate-50 rounded-2xl space-y-1.5 font-mono text-[11px]">
          <div class="flex justify-between text-slate-600">
            <span class="text-slate-400">服务端地址:</span>
            <span class="font-bold text-slate-800 break-all text-right">{{ connectionUrl }}</span>
          </div>
          <div class="flex justify-between text-slate-600">
            <span class="text-slate-400">网络往返延迟:</span>
            <span class="font-bold text-emerald-600">{{ rttMs }} ms</span>
          </div>
          <div class="flex justify-between text-slate-600">
            <span class="text-slate-400">心跳保活机制:</span>
            <span class="font-bold text-brand-600">10s (PING/PONG)</span>
          </div>
        </div>
      </div>

      <div class="pt-2 flex gap-3">
        <button
          @click="reconnect"
          class="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-2xl text-xs transition-all"
        >
          重新连接信道
        </button>
        <button
          @click="$emit('close')"
          class="flex-1 py-2.5 bg-brand-500 hover:bg-brand-600 text-white font-extrabold rounded-2xl text-xs shadow-pill transition-all"
        >
          确定
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { wsService } from '../../services/websocketService';

defineProps<{ visible: boolean }>();
defineEmits<{ (e: 'close'): void }>();

const agentStore = useAgentStore();
const wsConnected = computed(() => wsService.isConnected.value);
const connectionUrl = computed(() => wsService.connectionUrl.value);
const rttMs = computed(() => wsService.rttMs.value);

function reconnect() {
  wsService.connect(agentStore.workNo);
}
</script>
