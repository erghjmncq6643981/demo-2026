<template>
  <!-- 通话中悬浮控制条 (奢华轻量，深邃渐变) -->
  <div
    v-if="callStore.callState === 'CONNECTED'"
    class="mx-8 mt-4 mb-2 p-5 rounded-3xl bg-gradient-to-r from-[#1E1B4B] via-[#312E81] to-[#4338CA] text-white shadow-card flex items-center justify-between z-10 animate-in fade-in slide-in-from-top-4 duration-300"
  >
    <!-- 左侧: 呼叫状态与大号码 -->
    <div class="flex items-center gap-4">
      <div class="w-12 h-12 rounded-2xl bg-white/10 flex items-center justify-center text-2xl shadow-inner">
        📞
      </div>
      <div>
        <div class="flex items-center gap-3">
          <span class="font-extrabold font-mono text-xl tracking-wider">
            {{ callStore.currentCall?.callerNumber || '未知号码' }}
          </span>
          <span class="text-xs bg-white/20 px-2.5 py-0.5 rounded-full font-medium">
            {{ callStore.currentCall?.customerName || '在线通话中' }}
          </span>
          <span class="text-xs bg-emerald-400 text-slate-950 font-extrabold px-2.5 py-0.5 rounded-full">
            ● 已接通
          </span>
        </div>
        <div class="text-xs text-indigo-200 flex items-center gap-3 mt-1 font-mono">
          <span>通话计时: <strong class="text-amber-300 text-sm font-extrabold">{{ formatDuration(callStore.durationSeconds) }}</strong></span>
          <span>•</span>
          <span>线路: {{ callStore.currentCall?.didNumber || '-' }}</span>
        </div>
      </div>
    </div>

    <!-- 中间: VoIP 语音质量指标 -->
    <div class="hidden xl:flex items-center gap-3 bg-white/10 px-4 py-2 rounded-2xl border border-white/10 text-[11px] font-mono">
      <span class="text-indigo-200">链路质量:</span>
      <span class="text-emerald-300 font-bold flex items-center gap-1">● MOS 4.4 优</span>
      <span class="text-white/40">|</span>
      <span class="text-indigo-200">RTT: <strong class="text-white">18ms</strong></span>
      <span class="text-white/40">|</span>
      <span class="text-indigo-200">丢包: <strong class="text-emerald-300">0.0%</strong></span>
      <span class="text-white/40">|</span>
      <span class="text-indigo-200">编解码: <strong class="text-white">Opus-HD</strong></span>
    </div>

    <!-- 右侧: 电话控制操作组 -->
    <div class="flex items-center gap-2.5 text-xs font-bold">
      <button
        @click="callStore.toggleHold()"
        :class="[
          'px-4 py-2 rounded-2xl transition-all',
          callStore.isHeld ? 'bg-amber-400 text-slate-950 font-extrabold' : 'bg-white/15 hover:bg-white/25 text-white'
        ]"
      >
        {{ callStore.isHeld ? '恢复通话' : '保持' }}
      </button>

      <button
        @click="callStore.toggleMute()"
        :class="[
          'px-4 py-2 rounded-2xl transition-all',
          callStore.isMuted ? 'bg-amber-400 text-slate-950 font-extrabold' : 'bg-white/15 hover:bg-white/25 text-white'
        ]"
      >
        {{ callStore.isMuted ? '取消静音' : '静音' }}
      </button>

      <button
        @click="callStore.hangupCall()"
        class="px-6 py-2 bg-rose-500 hover:bg-rose-600 active:scale-95 text-white rounded-2xl shadow-sm transition-all flex items-center gap-1.5"
      >
        <span>✕</span>
        <span>挂断</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useCallStore } from '../../stores/callStore';

const callStore = useCallStore();

function formatDuration(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}
</script>
