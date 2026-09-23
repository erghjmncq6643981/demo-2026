<template>
  <!-- 
    话机/手机接听模式下的来电响铃弹屏 (对齐原型截图规范)
    注意：当接听方式为软电话 (WEBRTC) 时，由 SoftphoneDialer 软话机自身展示响铃动画，本居中弹屏自动静默
  -->
  <div
    v-if="shouldShowCenterModal"
    class="fixed inset-0 bg-slate-950/45 backdrop-blur-sm flex items-center justify-center z-50 p-4 transition-all animate-in fade-in duration-200 select-none"
  >
    <div class="w-full max-w-sm bg-white rounded-3xl shadow-2xl border border-slate-100 overflow-hidden relative animate-in zoom-in-95 duration-200">
      
      <!-- 1. 顶部蓝色拱形背景 + 居中响铃声波放射动画 (对齐原型截图 media_1789745507829.png) -->
      <div class="bg-gradient-to-r from-[#0284C7] via-[#0EA5E9] to-[#0284C7] pt-7 pb-10 px-6 relative text-white flex justify-center">
        <!-- 右上角关闭/拒接按键 -->
        <button
          @click="callStore.rejectCall()"
          class="absolute top-3.5 right-3.5 w-7 h-7 rounded-full bg-white/20 hover:bg-white/30 text-white flex items-center justify-center text-xs font-bold transition"
          title="关闭 / 拒接"
        >
          ✕
        </button>

        <!-- 居中电话呼叫图标 + 辐射声波动态涟漪 -->
        <div class="relative w-22 h-22 flex items-center justify-center -mb-6">
          <!-- 放射波纹 1 -->
          <span class="absolute inset-0 rounded-full bg-white/30 animate-ping duration-1000"></span>
          <!-- 放射波纹 2 -->
          <span class="absolute -inset-2.5 rounded-full border-2 border-white/40 animate-pulse"></span>
          <!-- 核心白色圆形底座 + 蓝色电话图标 -->
          <div class="relative w-20 h-20 rounded-full bg-white text-[#0284C7] flex items-center justify-center text-3xl shadow-xl ring-4 ring-white/30">
            📞
          </div>
        </div>
      </div>

      <!-- 2. 号码与接听提示主体 -->
      <div class="pt-8 pb-5 px-6 text-center">
        <!-- 大号电话号码 (真实主叫号码，无值即中性占位) -->
        <h3 class="text-2xl font-extrabold font-mono text-slate-900 tracking-wider">
          {{ callStore.currentCall?.callerNumber || '未知号码' }}
        </h3>
        
        <!-- 呼叫状态与来源线路 -->
        <p class="text-xs text-slate-500 font-medium mt-1">
          <span class="text-brand-600 font-bold">{{ isOutbound ? '外呼中...' : '呼叫中...' }}</span>
          <span class="mx-1.5 text-slate-300">|</span>
          <span>{{ callStore.currentCall?.flowName || (isOutbound ? '坐席外呼' : '来电') }}</span>
        </p>

        <!-- 坐席话机/手机提示胶囊 -->
        <div class="mt-3.5 p-2.5 bg-slate-50 rounded-2xl border border-slate-100 text-xs text-slate-600 flex items-center justify-center gap-2">
          <span>{{ agentStore.endpoint === 'SIP' ? '☎️' : '📱' }}</span>
          <span>
            请拿起
            <strong class="text-slate-900 font-bold">
              {{ agentStore.endpoint === 'SIP' ? `工位话机 (分机 ${agentStore.boundSipExtension || agentStore.extension})` : `随行手机 (${agentStore.boundMobile})` }}
            </strong>
            接听
          </span>
        </div>

        <!-- 真实可得的补充事实：路由依据 / 客户信息 / 同号码前序接待记录 -->
        <div v-if="callStore.currentCall?.routingReason" class="mt-2 text-[11px] text-slate-400">
          {{ callStore.currentCall?.routingReason }}
        </div>
        <div v-if="callStore.currentCall?.customerName || callStore.currentCall?.companyName" class="mt-1.5 text-[11px] text-slate-400">
          客户: {{ [callStore.currentCall?.companyName, callStore.currentCall?.customerName].filter(Boolean).join(' · ') }}
        </div>
        <div v-if="callStore.currentCall?.lastAgentName || callStore.currentCall?.lastCallTime" class="mt-1.5 text-[11px] text-amber-600 font-medium">
          上次接待: {{ callStore.currentCall?.lastAgentName || callStore.currentCall?.lastAgentWorkNo || '未记录' }}
          <template v-if="callStore.currentCall?.lastCallTime"> · {{ callStore.currentCall?.lastCallTime }}</template>
        </div>
      </div>

      <!-- 3. 外部终端只提供业务拒接；接听动作在实体终端完成。 -->
      <div class="p-6 pt-0 space-y-2">
        <p class="rounded-xl bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
          请直接在当前接听终端上接听，页面将等待话务事件确认。
        </p>
        <button
          @click="callStore.rejectCall()"
          class="w-full py-2.5 bg-slate-100 hover:bg-rose-50 hover:text-rose-600 text-slate-600 font-bold rounded-2xl text-xs transition flex items-center justify-center gap-1.5"
        >
          <span>✕</span>
          <span>结束通话 / 拒接</span>
        </button>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useCallStore } from '../../stores/callStore';
import { useAgentStore } from '../../stores/agentStore';

const callStore = useCallStore();
const agentStore = useAgentStore();

// 核心规则：仅当处于振铃态且接听方式不为软电话（即话机或手机接听）时，才显示主界面正中央的大响铃弹屏
const shouldShowCenterModal = computed(() => {
  return callStore.callState === 'RINGING' && agentStore.endpoint !== 'WEBRTC';
});

const isOutbound = computed(() => callStore.currentCall?.direction === 'OUTBOUND');
</script>
