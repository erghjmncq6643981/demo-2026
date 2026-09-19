<template>
  <footer class="h-9 bg-white border-t border-slate-100 px-8 flex items-center justify-between text-[11px] text-slate-400 select-none shrink-0">
    <div class="flex items-center gap-5">
      <span>当前分机: <strong class="text-slate-800 font-mono">{{ agentStore.extension }}</strong></span>
      <span>服务组: <strong class="text-slate-800">{{ agentStore.serviceGroup }}</strong></span>
      <span>音频设备: <strong class="text-slate-700">默认通信耳麦 (WebRTC)</strong></span>
    </div>

    <div class="flex items-center gap-4 font-mono">
      <span>今日呼入: <strong class="text-slate-700">{{ cdrStore.answeredCountToday }} 通</strong></span>
      <span>今日外呼: <strong class="text-slate-700">{{ cdrStore.outboundCountToday }} 通</strong></span>
      <span class="text-emerald-500 font-bold flex items-center gap-1">
        <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
        信令链路正常
      </span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { useAgentStore } from '../../stores/agentStore';
import { useCdrStore } from '../../stores/cdrStore';

const agentStore = useAgentStore();
const cdrStore = useCdrStore();
</script>
