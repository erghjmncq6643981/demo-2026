<template>
  <div class="px-4 sm:px-6 py-2 flex flex-wrap items-center justify-between gap-3 shrink-0 relative z-20">
    <!-- 左侧: 业务 Tab 切换 (未接待回拨为第一优先级，默认首屏展示) -->
    <div class="flex items-center gap-1.5 bg-white p-1 rounded-2xl border border-slate-200/80 shadow-xs text-xs">
      <!-- Tab 1: 未接待回拨记录 (优先级最高，在第一位) -->
      <button
        @click="currentTab = 'callback'"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-extrabold flex items-center gap-2 transition-all cursor-pointer',
          currentTab === 'callback' ? 'bg-brand-500 text-white shadow-pill' : 'text-slate-600 hover:text-slate-900'
        ]"
      >
        <span>未接待回拨</span>
        <span
          class="px-1.5 py-0.2 rounded-full text-[10px] font-mono font-bold"
          :class="currentTab === 'callback' ? 'bg-white/25 text-white' : 'bg-rose-100 text-rose-700'"
        >
          待办
        </span>
      </button>

      <!-- Tab 2: 通话话单记录 -->
      <button
        @click="currentTab = 'records'"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-extrabold transition-all cursor-pointer',
          currentTab === 'records' ? 'bg-brand-500 text-white shadow-pill' : 'text-slate-600 hover:text-slate-900'
        ]"
      >
        通话话单记录
      </button>

      <!-- Tab 3: 本人状态 -->
      <button
        @click="currentTab = 'agents'"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer',
          currentTab === 'agents' ? 'bg-brand-500 text-white shadow-pill font-extrabold' : 'text-slate-600 hover:text-slate-900'
        ]"
      >
        <span>👥</span>
        <span>本人状态</span>
        <span class="text-[10px] opacity-70 bg-black/10 px-1 py-0.2 rounded font-mono hidden md:inline">F2</span>
      </button>
    </div>

    <!-- 右侧: 智能外呼发起栏 -->
    <div class="flex items-center gap-2">
      <div class="relative flex items-center">
        <input
          v-model="outboundPhone"
          @keyup.enter="handleOutbound"
          type="text"
          placeholder="输入手机号或分机呼叫..."
          class="w-56 sm:w-64 h-9 bg-white border border-slate-200/90 rounded-full pl-4 pr-8 text-xs font-mono font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 shadow-2xs transition"
        />
        <button
          v-if="outboundPhone"
          @click="outboundPhone = ''"
          class="absolute right-2.5 text-slate-400 hover:text-slate-600 text-xs"
        >
          ✕
        </button>
      </div>

      <button
        @click="handleOutbound"
        :disabled="!outboundPhone.trim()"
        class="h-9 px-5 bg-brand-500 hover:bg-brand-600 active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed text-white font-extrabold text-xs rounded-full shadow-pill flex items-center gap-1.5 transition cursor-pointer"
      >
        <span>📞</span>
        <span>外呼</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useAgentStore } from '../../stores/agentStore';
import { triggerOutboundCall } from '../../api/telephonyApi';
import { toastError } from '../../utils/feedback';

const currentTab = defineModel<'callback' | 'records' | 'agents'>('currentTab', { default: 'callback' });

const outboundPhone = ref('');
const isCalling = ref(false);
const agentStore = useAgentStore();

async function handleOutbound() {
  const phone = outboundPhone.value.trim();
  if (!phone || isCalling.value) return;

  isCalling.value = true;
  try {
    const caller = agentStore.boundSipExtension || agentStore.extension || '';
    await triggerOutboundCall(
      agentStore.workNo,
      caller,
      phone,
      '',
      ''
    );
  } catch (e) {
    toastError(e instanceof Error ? e.message : '外呼失败');
  } finally {
    isCalling.value = false;
  }
}

function setOutboundPhone(phone: string) {
  outboundPhone.value = phone;
}

defineExpose({
  setOutboundPhone,
});
</script>
