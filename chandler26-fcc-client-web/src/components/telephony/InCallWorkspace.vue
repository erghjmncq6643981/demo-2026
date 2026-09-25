<template>
  <div class="flex-1 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-200">
    <p
      v-if="callStore.controlMessage"
      role="status"
      class="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
    >
      {{ callStore.controlMessage }}
    </p>

    <div class="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-3xl p-5 shadow-xl border border-white/10 flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4 min-w-0">
        <div class="w-13 h-13 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center text-2xl shadow-inner animate-pulse">
          🎧
        </div>
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-3">
            <span class="font-extrabold font-mono text-xl text-white tracking-wider break-all">
              {{ callStore.currentCall?.callerNumber || '未知号码' }}
            </span>
            <span class="text-xs bg-emerald-500 text-slate-950 font-extrabold px-2.5 py-0.5 rounded-full">
              {{ callStore.callState === 'ENDING' ? '等待结束事件' : '● 通话中' }}
            </span>
            <span
              v-if="callStore.currentCall?.customerName"
              class="text-xs bg-white/15 px-2.5 py-0.5 rounded-full font-medium text-slate-200"
            >
              {{ callStore.currentCall.customerName }}
            </span>
          </div>
          <div class="text-xs text-slate-300 flex flex-wrap items-center gap-x-3 gap-y-1 mt-1.5 font-mono">
            <span>{{ directionLabel }}</span>
            <span>线路号码: {{ callStore.currentCall?.didNumber || '未提供' }}</span>
            <span>流程: {{ callStore.currentCall?.flowName || '未提供' }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3 bg-white/10 px-5 py-2.5 rounded-2xl border border-white/10">
        <div>
          <div class="text-[10px] text-slate-300">通话时长</div>
          <div class="font-mono text-xl font-extrabold text-emerald-300 tracking-wider">
            {{ formattedDuration }}
          </div>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2.5 text-xs font-bold">
        <button
          @click="callStore.toggleHold()"
          :disabled="callStore.holdPending || callStore.callState === 'ENDING'"
          :class="[
            'px-4 py-2.5 rounded-2xl border transition-all flex items-center gap-1.5 disabled:opacity-50',
            callStore.holdRequested
              ? 'bg-amber-400 text-slate-950 border-amber-400 font-extrabold'
              : 'bg-white/10 hover:bg-white/20 text-white border-white/15'
          ]"
        >
          {{ callStore.holdPending ? '提交中' : callStore.holdRequested ? '请求恢复' : '请求保持' }}
        </button>

        <button
          v-if="agentStore.endpoint === 'WEBRTC'"
          @click="callStore.toggleMute()"
          :disabled="callStore.callState === 'ENDING'"
          :class="[
            'px-4 py-2.5 rounded-2xl border transition-all flex items-center gap-1.5 disabled:opacity-50',
            callStore.isMuted
              ? 'bg-amber-400 text-slate-950 border-amber-400 font-extrabold'
              : 'bg-white/10 hover:bg-white/20 text-white border-white/15'
          ]"
        >
          {{ callStore.isMuted ? '🎙️ 取消静音' : '🔇 静音' }}
        </button>
        <span v-else class="px-3 py-2 text-slate-300 border border-white/10 rounded-2xl">
          静音由话机控制
        </span>

        <button
          @click="openTransferModal"
          :disabled="callStore.callState === 'ENDING'"
          class="px-4 py-2.5 rounded-2xl border bg-indigo-500/30 hover:bg-indigo-500/50 text-indigo-200 border-indigo-400/40 transition-all disabled:opacity-50"
        >
          ↪️ 转接
        </button>

        <button
          @click="handleHangup"
          :disabled="callStore.callState === 'ENDING'"
          class="px-6 py-2.5 bg-rose-500 hover:bg-rose-600 active:scale-95 text-white font-extrabold rounded-2xl shadow-lg shadow-rose-500/30 transition-all disabled:opacity-50"
        >
          ✕ {{ callStore.callState === 'ENDING' ? '结束中' : '挂断电话' }}
        </button>
      </div>
    </div>

    <div class="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 min-h-0">
      <section class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-y-auto">
        <h3 class="text-base font-extrabold text-slate-900">本次通话事实</h3>
        <p class="mt-1 text-xs text-slate-400">仅展示话务服务已确认并推送的数据。</p>
        <dl class="mt-5 grid grid-cols-[7rem_minmax(0,1fr)] gap-x-4 gap-y-4 text-sm">
          <dt class="text-slate-400">业务通话标识</dt>
          <dd class="font-mono text-slate-800 break-all">{{ callStore.currentCall?.callId || '未提供' }}</dd>
          <dt class="text-slate-400">呼叫方向</dt>
          <dd class="font-semibold text-slate-800">{{ directionLabel }}</dd>
          <dt class="text-slate-400">线路号码</dt>
          <dd class="font-mono text-slate-800 break-all">{{ callStore.currentCall?.didNumber || '未提供' }}</dd>
          <dt class="text-slate-400">命中流程</dt>
          <dd class="text-slate-800 break-words">{{ callStore.currentCall?.flowName || '未提供' }}</dd>
          <dt class="text-slate-400">流程轨迹</dt>
          <dd class="text-slate-800 break-words">{{ callStore.currentCall?.ivrPath || '尚未推送' }}</dd>
          <dt class="text-slate-400">路由依据</dt>
          <dd class="text-slate-800 break-words">{{ callStore.currentCall?.routingReason || '尚未推送' }}</dd>
        </dl>
      </section>

      <section class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-y-auto">
        <h3 class="text-base font-extrabold text-slate-900">客户与历史上下文</h3>
        <p class="mt-1 text-xs text-slate-400">不使用本地示例数据；缺失字段保持明确的未接入状态。</p>

        <div v-if="hasCustomerContext" class="mt-5 space-y-4 text-sm">
          <div v-if="callStore.currentCall?.customerName || callStore.currentCall?.companyName" class="rounded-2xl bg-slate-50 p-4">
            <div class="text-xs text-slate-400">客户</div>
            <div class="mt-1 font-semibold text-slate-800 break-words">
              {{ [callStore.currentCall?.companyName, callStore.currentCall?.customerName].filter(Boolean).join(' · ') }}
            </div>
          </div>
          <div v-if="hasPreviousCall" class="rounded-2xl border border-slate-100 p-4 space-y-2">
            <div class="flex flex-wrap justify-between gap-2">
              <span class="text-slate-400">上次接待坐席</span>
              <strong class="text-slate-800">
                {{ callStore.currentCall?.lastAgentName || callStore.currentCall?.lastAgentWorkNo || '未提供' }}
              </strong>
            </div>
            <div class="flex flex-wrap justify-between gap-2">
              <span class="text-slate-400">上次通话时间</span>
              <span class="font-mono text-slate-700">{{ callStore.currentCall?.lastCallTime || '未提供' }}</span>
            </div>
            <div v-if="callStore.currentCall?.lastCallSummary" class="pt-2 border-t border-slate-100">
              <div class="text-xs text-slate-400">上次通话摘要</div>
              <p class="mt-1 text-slate-700 whitespace-pre-wrap break-words">{{ callStore.currentCall.lastCallSummary }}</p>
            </div>
          </div>
        </div>

        <div v-else class="mt-5 min-h-44 rounded-2xl border border-dashed border-slate-200 bg-slate-50 flex flex-col items-center justify-center text-center p-6">
          <span class="text-3xl">📋</span>
          <strong class="mt-3 text-sm text-slate-700">暂无授权业务资料</strong>
          <span class="mt-1 text-xs text-slate-400">客户与订单查询接口尚未接入坐席客户端。</span>
        </div>
      </section>
    </div>

    <div v-if="isTransferModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-2xl space-y-4 text-slate-800">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900">呼叫转接 / 盲转</h3>
          <button @click="isTransferModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold">&times;</button>
        </div>
        <p class="text-xs text-slate-500 leading-relaxed">
          输入目标坐席工号或分机号。转接指令受理后，页面将等待真实话务事件确认。
        </p>
        <div v-if="transferError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-600 font-bold">
          {{ transferError }}
        </div>
        <div class="space-y-2">
          <label class="block text-xs font-bold text-slate-700">目标坐席工号 / 分机号</label>
          <input
            v-model="transferTarget"
            type="text"
            placeholder="输入目标坐席工号或分机号"
            class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
            @keyup.enter="handleTransferSubmit"
          />
        </div>
        <div class="pt-3 border-t border-slate-100 flex justify-end space-x-2">
          <button @click="isTransferModalOpen = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold">
            取消
          </button>
          <button
            @click="handleTransferSubmit"
            :disabled="transferring"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition disabled:opacity-50"
          >
            {{ transferring ? '正在下发转接' : '确认转接' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { triggerTransferCall } from '../../api/telephonyApi';
import { useAgentStore } from '../../stores/agentStore';
import { useCallStore } from '../../stores/callStore';
import { toast } from '../../utils/feedback';

const callStore = useCallStore();
const agentStore = useAgentStore();
const isTransferModalOpen = ref(false);
const transferTarget = ref('');
const transferring = ref(false);
const transferError = ref('');

const formattedDuration = computed(() => {
  const minutes = Math.floor(callStore.durationSeconds / 60);
  const seconds = callStore.durationSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
});

const directionLabel = computed(() => callStore.currentCall?.direction === 'OUTBOUND' ? '外呼' : '呼入');

const hasPreviousCall = computed(() => Boolean(
  callStore.currentCall?.lastAgentName
  || callStore.currentCall?.lastAgentWorkNo
  || callStore.currentCall?.lastCallTime
  || callStore.currentCall?.lastCallSummary,
));

const hasCustomerContext = computed(() => Boolean(
  callStore.currentCall?.customerName
  || callStore.currentCall?.companyName
  || hasPreviousCall.value,
));

function handleHangup(): void {
  void callStore.hangupCall();
}

function openTransferModal(): void {
  transferTarget.value = '';
  transferError.value = '';
  isTransferModalOpen.value = true;
}

async function handleTransferSubmit(): Promise<void> {
  const target = transferTarget.value.trim();
  const callId = callStore.currentCall?.callId;
  if (!callId) {
    transferError.value = '业务通话尚未关联，无法转接';
    return;
  }
  if (!target) {
    transferError.value = '请输入目标坐席工号或分机号';
    return;
  }

  transferring.value = true;
  transferError.value = '';
  try {
    const response = await triggerTransferCall(agentStore.workNo, target, callId);
    isTransferModalOpen.value = false;
    toast(response.message || '转接指令已受理，等待话务事件确认', 'info');
  } catch (error) {
    transferError.value = error instanceof Error ? error.message : '转接信令下发异常';
  } finally {
    transferring.value = false;
  }
}
</script>
