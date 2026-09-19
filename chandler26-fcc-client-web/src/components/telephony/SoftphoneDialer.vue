<template>
  <div v-if="agentStore.endpoint === 'WEBRTC'" class="fixed bottom-6 right-6 z-40 flex flex-col items-end select-none">
    <!-- 1. 软电话展开面板 -->
    <transition
      enter-active-class="transition ease-out duration-250 transform"
      enter-from-class="opacity-0 translate-y-6 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition ease-in duration-200 transform"
      leave-from-class="opacity-100 translate-y-0 scale-100"
      leave-to-class="opacity-0 translate-y-6 scale-95"
    >
      <div
        v-if="isOpen"
        class="w-80 bg-white/95 backdrop-blur-md rounded-3xl shadow-2xl border border-slate-200/90 overflow-hidden mb-3.5 transition-all"
      >
        <!-- 话机顶部状态指示条 -->
        <div class="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white px-4 py-3 flex items-center justify-between border-b border-white/10">
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full" :class="statusDotClass"></span>
            <span class="text-xs font-extrabold tracking-wide font-mono">{{ statusTitle }}</span>
          </div>
          <div class="flex items-center gap-2">
            <!-- 极简模式信息 -->
            <span class="text-[10px] text-slate-400 font-mono">WebRTC</span>
            <button
              @click="toggleOpen"
              class="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white flex items-center justify-center text-xs transition"
              title="隐藏软电话 (按下方浮动球重新展开)"
            >
              ─
            </button>
          </div>
        </div>

        <!-- 响铃动态提醒区 (仅软电话接听模式且处于振铃态时显现) -->
        <div
          v-if="isRinging"
          class="bg-gradient-to-b from-brand-50 to-white p-4 text-center border-b border-brand-100 relative overflow-hidden"
        >
          <!-- 辐射声波涟漪 -->
          <div class="relative w-14 h-14 mx-auto mb-2 flex items-center justify-center">
            <span class="absolute inset-0 rounded-full bg-brand-500/20 animate-ping duration-1000"></span>
            <span class="absolute -inset-2 rounded-full bg-brand-500/10 animate-pulse"></span>
            <div class="relative w-12 h-12 rounded-full bg-brand-600 text-white flex items-center justify-center text-xl shadow-md">
              📞
            </div>
          </div>
          <div class="text-xs font-bold text-brand-700">来电振铃中</div>
          <div class="text-lg font-extrabold font-mono text-slate-900 mt-0.5">
            {{ callStore.currentCall?.callerNumber || dialedNumber || '未知号码' }}
          </div>
          <div class="text-[11px] text-slate-500">
            {{ callStore.currentCall?.customerName || (callStore.currentCall?.direction === 'OUTBOUND' ? '外呼中' : '来电') }}
            <template v-if="callStore.currentCall?.flowName"> · {{ callStore.currentCall?.flowName }}</template>
          </div>
        </div>

        <!-- 通话中秒表与状态 (仅接通态) -->
        <div
          v-else-if="isConnected"
          class="bg-emerald-50/80 px-4 py-3 border-b border-emerald-100 flex items-center justify-between"
        >
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-xl bg-emerald-500 text-white flex items-center justify-center text-sm shadow-xs animate-pulse">
              🎧
            </div>
            <div>
              <div class="text-xs font-extrabold text-slate-900 font-mono">
                {{ callStore.currentCall?.callerNumber || dialedNumber || '未知号码' }}
              </div>
              <div class="text-[10px] text-emerald-700 font-medium">
                {{ callStore.currentCall?.customerName || '在线通话中' }}
              </div>
            </div>
          </div>
          <div class="text-right">
            <div class="font-mono text-base font-extrabold text-emerald-600">
              {{ formattedDuration }}
            </div>
            <div class="text-[9px] text-slate-400">持续时长</div>
          </div>
        </div>

        <!-- 拨号数字液晶输入区 (空闲态) -->
        <div v-else class="p-4 pb-2">
          <div class="bg-slate-50 border border-slate-200/80 rounded-2xl p-3 flex items-center justify-between">
            <input
              v-model="dialedNumber"
              @keyup.enter="handleCall"
              type="text"
              placeholder="输入号码或点按键盘"
              class="w-full bg-transparent font-mono font-extrabold text-lg text-slate-900 tracking-wider placeholder:text-slate-300 placeholder:text-xs placeholder:font-normal focus:outline-none"
            />
            <div class="flex items-center gap-1.5 shrink-0 pl-2">
              <button
                v-if="dialedNumber"
                @click="backspace"
                class="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-200/60 flex items-center justify-center text-xs font-bold transition"
                title="退格删除"
              >
                ⌫
              </button>
              <button
                v-if="dialedNumber"
                @click="dialedNumber = ''"
                class="w-7 h-7 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 flex items-center justify-center text-xs font-bold transition"
                title="清空"
              >
                ✕
              </button>
            </div>
          </div>
        </div>

        <!-- 3x4 经典电话数字按键组 -->
        <div class="px-4 py-2 grid grid-cols-3 gap-2.5">
          <button
            v-for="k in keypad"
            :key="k.key"
            @click="pressKey(k.key)"
            class="h-12 rounded-2xl bg-slate-50 hover:bg-slate-100 active:scale-95 active:bg-slate-200 border border-slate-200/70 flex flex-col items-center justify-center transition-all cursor-pointer shadow-2xs group"
          >
            <span class="font-mono font-bold text-base text-slate-800 leading-none group-hover:text-brand-600">
              {{ k.key }}
            </span>
            <span v-if="k.sub" class="text-[9px] font-mono text-slate-400 tracking-widest leading-none mt-0.5">
              {{ k.sub }}
            </span>
          </button>
        </div>

        <!-- 底部核心呼叫与控制操作条 -->
        <div class="p-4 pt-2">
          <!-- 1. 振铃状态控制: 接听 / 拒接 -->
          <div v-if="isRinging" class="grid grid-cols-2 gap-3">
            <button
              @click="callStore.rejectCall()"
              class="py-3 bg-rose-50 hover:bg-rose-100 active:scale-95 text-rose-600 font-extrabold rounded-2xl text-xs flex items-center justify-center gap-1.5 transition"
            >
              <span>✕</span>
              <span>拒接</span>
            </button>
            <button
              @click="callStore.answerCall()"
              class="py-3 bg-emerald-500 hover:bg-emerald-600 active:scale-95 text-white font-extrabold rounded-2xl text-xs flex items-center justify-center gap-1.5 shadow-md shadow-emerald-500/30 transition animate-pulse"
            >
              <span>✓</span>
              <span>接听</span>
            </button>
          </div>

          <!-- 2. 通话中控制: 挂断 / 静音 / 保持 -->
          <div v-else-if="isConnected" class="space-y-2">
            <div class="grid grid-cols-2 gap-2">
              <button
                @click="callStore.toggleMute()"
                :class="[
                  'py-2.5 rounded-xl font-bold text-xs flex items-center justify-center gap-1 transition',
                  callStore.isMuted ? 'bg-amber-100 text-amber-800 border border-amber-300' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                ]"
              >
                <span>{{ callStore.isMuted ? '🎙️ 取消静音' : '🔇 静音' }}</span>
              </button>
              <button
                @click="callStore.toggleHold()"
                :class="[
                  'py-2.5 rounded-xl font-bold text-xs flex items-center justify-center gap-1 transition',
                  callStore.isHeld ? 'bg-amber-100 text-amber-800 border border-amber-300' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                ]"
              >
                <span>{{ callStore.holdPending ? '提交中' : callStore.holdRequested ? '请求恢复' : '请求保持' }}</span>
              </button>
            </div>
            <button
              @click="callStore.hangupCall()"
              class="w-full py-3 bg-rose-500 hover:bg-rose-600 active:scale-95 text-white font-extrabold rounded-2xl text-xs flex items-center justify-center gap-1.5 shadow-md shadow-rose-500/30 transition"
            >
              <span>✕</span>
              <span>结束通话</span>
            </button>
          </div>

          <!-- 3. 空闲待机状态: 发起呼叫 -->
          <div v-else>
            <button
              @click="handleCall"
              :disabled="!dialedNumber.trim()"
              class="w-full py-3.5 bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 active:scale-98 disabled:opacity-40 disabled:cursor-not-allowed text-white font-extrabold text-sm rounded-2xl shadow-lg shadow-brand-500/25 flex items-center justify-center gap-2 transition"
            >
              <span>📞</span>
              <span>发起呼叫</span>
            </button>
          </div>
        </div>

      </div>
    </transition>

    <!-- 2. 悬浮展开/隐藏软电话微控制球 -->
    <button
      @click="toggleOpen"
      class="group relative flex items-center gap-2 bg-gradient-to-r from-slate-900 to-indigo-950 hover:from-slate-800 hover:to-indigo-900 text-white px-4 py-3 rounded-full shadow-2xl border border-white/20 transition-all cursor-pointer active:scale-95"
      :class="[
        isRinging ? 'ring-4 ring-brand-500/50 animate-bounce' : '',
        isOpen ? 'ring-2 ring-indigo-500/40' : ''
      ]"
      title="点击展开/隐藏软电话话机"
    >
      <!-- 状态微灯 -->
      <span class="w-2.5 h-2.5 rounded-full" :class="statusDotClass"></span>
      <span class="text-xs font-extrabold tracking-wide flex items-center gap-1.5">
        <span>🎧</span>
        <span>{{ isOpen ? '隐藏软话机' : '软电话' }}</span>
      </span>

      <!-- 振铃提示微标签 -->
      <span
        v-if="isRinging"
        class="absolute -top-2 -right-2 px-2 py-0.5 bg-rose-500 text-white rounded-full text-[10px] font-extrabold animate-pulse shadow-sm"
      >
        来电
      </span>
      <span
        v-else-if="isConnected"
        class="absolute -top-2 -right-2 px-2 py-0.5 bg-emerald-500 text-white rounded-full text-[10px] font-extrabold font-mono shadow-sm"
      >
        通话中
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useCallStore } from '../../stores/callStore';
import { useAgentStore } from '../../stores/agentStore';
import { audioService } from '../../services/audioService';
import { sipWebRtcService } from '../../services/sipWebRtcService';
import { triggerOutboundCall } from '../../api/telephonyApi';

const callStore = useCallStore();
const agentStore = useAgentStore();

const isOpen = ref(false);
const dialedNumber = ref('');

const keypad = [
  { key: '1', sub: '' },
  { key: '2', sub: 'ABC' },
  { key: '3', sub: 'DEF' },
  { key: '4', sub: 'GHI' },
  { key: '5', sub: 'JKL' },
  { key: '6', sub: 'MNO' },
  { key: '7', sub: 'PQRS' },
  { key: '8', sub: 'TUV' },
  { key: '9', sub: 'WXYZ' },
  { key: '*', sub: '' },
  { key: '0', sub: '+' },
  { key: '#', sub: '' },
];

const isRinging = computed(() => callStore.callState === 'RINGING');
const isConnected = computed(() => callStore.callState === 'CONNECTED');

// 核心规则：当接听方式为软电话时，来电响铃软电话自动展开以显示动画
watch(
  () => callStore.callState,
  (state) => {
    if (state === 'RINGING' && agentStore.endpoint === 'WEBRTC') {
      isOpen.value = true;
    }
  }
);

const statusTitle = computed(() => {
  if (isRinging.value) return '来电振铃中...';
  if (isConnected.value) return '通话接通中 (WebRTC)';
  if (callStore.callState === 'ACW') return '话后整理';
  if (sipWebRtcService.registrationState.value === 'REGISTERED') return '软话机 · 在线已注册 (WebRTC)';
  if (sipWebRtcService.registrationState.value === 'CONNECTING') return '软话机 · 正在连接软交换...';
  return '软话机 · 就绪待命';
});

const statusDotClass = computed(() => {
  if (isRinging.value) return 'bg-amber-400 animate-ping';
  if (isConnected.value) return 'bg-emerald-400 animate-pulse';
  if (sipWebRtcService.registrationState.value === 'REGISTERED') return 'bg-emerald-400';
  return 'bg-amber-400';
});

const formattedDuration = computed(() => {
  const sec = callStore.durationSeconds;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
});

function toggleOpen() {
  isOpen.value = !isOpen.value;
}

function openWithNumber(num: string) {
  dialedNumber.value = num;
  isOpen.value = true;
}

function pressKey(key: string) {
  audioService.playDtmf(key);
  if (!isConnected.value) {
    dialedNumber.value += key;
  } else {
    // 通话中发送真实二次 DTMF
    callStore.sendDtmf(key);
  }
}

function backspace() {
  if (dialedNumber.value.length > 0) {
    dialedNumber.value = dialedNumber.value.slice(0, -1);
  }
}

async function handleCall() {
  const num = dialedNumber.value.trim();
  if (!num) return;

  if (sipWebRtcService.isRegistered.value) {
    const ok = sipWebRtcService.call(num);
    if (ok) {
      callStore.startOutbound();
      return;
    }
  }

  try {
    const caller = agentStore.boundSipExtension || agentStore.extension || agentStore.workNo;
    const response = await triggerOutboundCall(
      agentStore.workNo,
      caller,
      num,
    );
    callStore.startOutbound(response.data?.callId);
  } catch (e) {
    console.error('Softphone call failed:', e);
  }
}

defineExpose({
  toggleOpen,
  openWithNumber,
  isOpen,
});
</script>
