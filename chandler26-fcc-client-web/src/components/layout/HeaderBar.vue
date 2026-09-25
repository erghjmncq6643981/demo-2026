<template>
  <header class="h-12 bg-white border-b border-slate-100 px-4 sm:px-6 flex items-center justify-between shrink-0 select-none z-30">
    <!-- 左侧: 系统标题与品牌标识 -->
    <div class="flex items-center gap-3">
      <div class="flex items-center gap-1.5">
        <span class="w-2.5 h-2.5 rounded-full bg-[#FF5F56] inline-block opacity-80"></span>
        <span class="w-2.5 h-2.5 rounded-full bg-[#FFBD2E] inline-block opacity-80"></span>
        <span class="w-2.5 h-2.5 rounded-full bg-[#27C93F] inline-block opacity-80"></span>
      </div>
      <div class="h-3.5 w-[1px] bg-slate-200"></div>
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-600 flex items-center justify-center text-white text-xs shadow-xs">
          🎧
        </div>
        <span class="font-extrabold text-xs sm:text-sm text-slate-900 tracking-tight">FCC 坐席工作台</span>
      </div>
    </div>

    <!-- 右侧: 快捷操作与信道状态 -->
    <div class="flex items-center gap-2 sm:gap-3 text-xs">
      <!-- 软电话面板显隐控制按钮 (仅接听方式为软话机 WebRTC 时显示) -->
      <button
        v-if="agentStore.endpoint === 'WEBRTC'"
        @click="$emit('toggleSoftphone')"
        class="flex items-center gap-1.5 px-3 py-1 bg-slate-100 hover:bg-slate-200 active:scale-95 text-slate-700 font-bold rounded-full transition shadow-2xs cursor-pointer"
        title="点击展开/收起软电话拨号盘"
      >
        <span>🎧</span>
        <span class="hidden sm:inline">软电话</span>
      </button>

      <!-- SIP 状态 -->
      <div :class="['hidden md:flex items-center gap-1 border rounded-full px-2.5 py-0.5 text-[11px] font-mono font-bold', endpointStateClass]">
        <span :class="['w-1.5 h-1.5 rounded-full', endpointDotClass]"></span>
        <span>{{ endpointStateLabel }}</span>
      </div>

      <!-- WebSocket 状态 -->
      <button
        @click="$emit('openWsDiagnostics')"
        :class="[
          'flex items-center gap-1 border rounded-full px-2.5 py-0.5 text-[11px] font-mono font-bold transition cursor-pointer',
          wsConnected ? 'bg-indigo-50 border-indigo-200 text-brand-700' : 'bg-rose-50 border-rose-200 text-rose-700'
        ]"
        title="点击查看 WebSocket 状态"
      >
        <span :class="['w-1.5 h-1.5 rounded-full', wsConnected ? 'bg-emerald-500' : 'bg-rose-500']"></span>
        <span class="hidden sm:inline">{{ wsConnected ? 'WS在线' : 'WS离线' }}</span>
      </button>

      <!-- 退出登录 -->
      <button
        @click="handleLogout"
        class="px-2.5 py-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg text-xs font-medium transition cursor-pointer"
        title="退出登录"
      >
        退出
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { wsService } from '../../services/websocketService';
import { sipWebRtcService } from '../../services/sipWebRtcService';
import { confirmAction } from '../../utils/feedback';

defineEmits<{
  (e: 'openWsDiagnostics'): void;
  (e: 'toggleSoftphone'): void;
}>();

const agentStore = useAgentStore();
const wsConnected = computed(() => wsService.isConnected.value);

const endpointStateLabel = computed(() => {
  if (agentStore.endpoint === 'SIP') {
    const ext = agentStore.boundSipExtension || agentStore.extension;
    return ext ? `SIP话机 (${ext})` : 'SIP话机';
  }
  if (agentStore.endpoint === 'MOBILE') return '手机';
  switch (sipWebRtcService.registrationState.value) {
    case 'REGISTERED': return 'WebRTC 已注册';
    case 'CONNECTING': return 'WebRTC 注册中';
    case 'REGISTRATION_FAILED': return 'WebRTC 注册失败';
    default: return 'WebRTC 未注册';
  }
});

const endpointStateClass = computed(() => {
  if (agentStore.endpoint === 'SIP') {
    return 'bg-emerald-50 border-emerald-200 text-emerald-700';
  }
  if (agentStore.endpoint === 'MOBILE') {
    return 'bg-slate-50 border-slate-200 text-slate-600';
  }
  if (sipWebRtcService.registrationState.value === 'REGISTERED') {
    return 'bg-emerald-50 border-emerald-200 text-emerald-700';
  }
  if (sipWebRtcService.registrationState.value === 'REGISTRATION_FAILED') {
    return 'bg-rose-50 border-rose-200 text-rose-700';
  }
  return 'bg-amber-50 border-amber-200 text-amber-700';
});

const endpointDotClass = computed(() => {
  if (agentStore.endpoint === 'SIP') {
    return 'bg-emerald-500';
  }
  if (agentStore.endpoint === 'MOBILE') {
    return 'bg-slate-400';
  }
  if (sipWebRtcService.registrationState.value === 'REGISTERED') return 'bg-emerald-500';
  if (sipWebRtcService.registrationState.value === 'REGISTRATION_FAILED') return 'bg-rose-500';
  return 'bg-amber-500';
});

async function handleLogout() {
  const ok = await confirmAction('确认注销当前坐席登录状态吗？', { title: '退出登录', confirmText: '退出登录' });
  if (ok) {
    await agentStore.logout();
  }
}
</script>
