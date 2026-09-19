<template>
  <!-- 通话中沉浸式业务工作台 (方便通话过程中快速查询运单、客户档案与记录便签) -->
  <div class="flex-1 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-200">
    
    <!-- 1. 顶部通话核心态势与话务控制卡片 -->
    <div class="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-3xl p-5 shadow-xl border border-white/10 flex flex-wrap items-center justify-between gap-4">
      <!-- 左侧: 客户呼叫信息 -->
      <div class="flex items-center gap-4">
        <div class="w-13 h-13 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center text-2xl shadow-inner animate-pulse">
          🎧
        </div>
        <div>
          <div class="flex items-center gap-3">
            <span class="font-extrabold font-mono text-xl text-white tracking-wider">
              {{ callStore.currentCall?.callerNumber || '未知号码' }}
            </span>
            <span class="text-xs bg-emerald-500 text-slate-950 font-extrabold px-2.5 py-0.5 rounded-full">
              ● 通话中
            </span>
            <span
              v-if="callStore.currentCall?.customerName"
              class="text-xs bg-white/15 px-2.5 py-0.5 rounded-full font-medium text-slate-200"
            >
              {{ callStore.currentCall?.customerName }}
            </span>
            <span
              v-if="callStore.currentCall?.companyName"
              class="text-[11px] bg-white/15 px-2 py-0.5 rounded-full font-medium text-slate-200"
            >
              {{ callStore.currentCall?.companyName }}
            </span>
          </div>
          <div class="text-xs text-slate-300 flex items-center gap-3 mt-1.5 font-mono">
            <span>{{ callStore.currentCall?.direction === 'OUTBOUND' ? '外呼' : '呼入' }}</span>
            <span>•</span>
            <span>线路号码: {{ callStore.currentCall?.didNumber || '-' }}</span>
            <span>•</span>
            <span>流程: {{ callStore.currentCall?.flowName || '-' }}</span>
          </div>
        </div>
      </div>

      <!-- 中间: 实时跳秒计时器与音频动态波纹 -->
      <div class="flex items-center gap-3 bg-white/10 px-5 py-2.5 rounded-2xl border border-white/10">
        <div class="flex items-center gap-1">
          <span
            v-for="i in 5"
            :key="i"
            class="w-1 bg-emerald-400 rounded-full animate-pulse"
            :style="{ height: `${(i % 3 + 1) * 8}px`, animationDelay: `${i * 150}ms` }"
          ></span>
        </div>
        <div>
          <div class="text-[10px] text-slate-300">通话时长</div>
          <div class="font-mono text-xl font-extrabold text-emerald-300 tracking-wider">
            {{ formattedDuration }}
          </div>
        </div>
      </div>

      <!-- 右侧: 电话硬控按键组 -->
      <div class="flex items-center gap-2.5 text-xs font-bold">
        <button
          @click="callStore.toggleHold()"
          :class="[
            'px-4 py-2.5 rounded-2xl border transition-all flex items-center gap-1.5 cursor-pointer',
            callStore.isHeld ? 'bg-amber-400 text-slate-950 border-amber-400 font-extrabold' : 'bg-white/10 hover:bg-white/20 text-white border-white/15'
          ]"
        >
          <span>{{ callStore.isHeld ? '▶ 恢复通话' : '⏸️ 保持' }}</span>
        </button>

        <button
          @click="callStore.toggleMute()"
          :class="[
            'px-4 py-2.5 rounded-2xl border transition-all flex items-center gap-1.5 cursor-pointer',
            callStore.isMuted ? 'bg-amber-400 text-slate-950 border-amber-400 font-extrabold' : 'bg-white/10 hover:bg-white/20 text-white border-white/15'
          ]"
        >
          <span>{{ callStore.isMuted ? '🎙️ 取消静音' : '🔇 静音' }}</span>
        </button>

        <!-- 呼叫转接按钮 -->
        <button
          @click="openTransferModal"
          class="px-4 py-2.5 rounded-2xl border bg-indigo-500/30 hover:bg-indigo-500/50 text-indigo-200 border-indigo-400/40 transition-all flex items-center gap-1.5 cursor-pointer font-extrabold"
        >
          <span>↪️ 转接</span>
        </button>

        <!-- 挂断电话按钮 (挂断后平滑返回原界面) -->
        <button
          @click="handleHangupAndReturn"
          class="px-6 py-2.5 bg-rose-500 hover:bg-rose-600 active:scale-95 text-white font-extrabold rounded-2xl shadow-lg shadow-rose-500/30 transition-all flex items-center gap-2 cursor-pointer"
        >
          <span>✕</span>
          <span>挂断电话</span>
        </button>
      </div>
    </div>

    <!-- 2. 核心业务支撑区域 (边通话边查询运单/订单、客户画像与便签) -->
    <div class="flex-1 grid grid-cols-1 lg:grid-cols-3 gap-4 min-h-0">
      
      <!-- 左侧两栏: 运单/订单快速业务检索与明细展示 -->
      <div class="lg:col-span-2 bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex flex-col">
        <!-- 业务检索控制栏 -->
        <div class="flex items-center justify-between pb-4 border-b border-slate-100 gap-4">
          <div class="flex items-center gap-2">
            <span class="text-base font-extrabold text-slate-900">📦 客户业务单据快速查询</span>
            <span class="text-xs text-slate-400 font-normal">已按当前主叫号码自动关联</span>
          </div>

          <div class="flex items-center gap-2">
            <div class="relative">
              <input
                v-model="searchKeyword"
                type="text"
                placeholder="输入运单号/车牌号/订单号查询..."
                class="w-64 bg-slate-50 border border-slate-200 rounded-xl pl-3 pr-8 py-1.5 text-xs font-mono text-slate-800 focus:outline-none focus:border-brand-500 focus:bg-white"
              />
              <span class="absolute right-2.5 top-2 text-slate-400 text-xs">🔍</span>
            </div>
            <button
              @click="refreshBusinessData"
              class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold transition"
            >
              刷新
            </button>
          </div>
        </div>

        <!-- 关联业务运单列表 -->
        <div class="flex-1 overflow-y-auto mt-4 space-y-3">
          <div v-if="filteredOrders.length === 0" class="h-64 flex flex-col items-center justify-center text-slate-400">
            <span class="text-3xl">📦</span>
            <span class="mt-2 text-xs font-medium">当前主叫客户名下暂无关联运单</span>
            <span class="mt-1 text-[11px] text-slate-300">可在上方输入单号手动检索关联</span>
          </div>

          <div
            v-for="order in filteredOrders"
            :key="order.orderNo"
            class="p-4 rounded-2xl border transition-all cursor-pointer"
            :class="selectedOrderNo === order.orderNo ? 'border-brand-500 bg-brand-50/30 shadow-xs' : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/50'"
            @click="selectedOrderNo = order.orderNo"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="font-mono font-bold text-sm text-slate-900">{{ order.orderNo }}</span>
                <span class="px-2 py-0.5 rounded-md text-[10px] font-extrabold bg-blue-50 text-blue-700">
                  {{ order.goodsType }}
                </span>
                <span
                  class="px-2 py-0.5 rounded-md text-[10px] font-extrabold"
                  :class="order.status === 'TRANSPORTING' ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'"
                >
                  {{ order.statusText }}
                </span>
              </div>
              <span class="text-xs text-slate-400 font-mono">{{ order.createTime }}</span>
            </div>

            <!-- 路线与司机信息 -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs text-slate-600 bg-slate-50/80 p-2.5 rounded-xl">
              <div>
                <span class="text-slate-400">运输路线:</span>
                <strong class="text-slate-800 ml-1">{{ order.origin }} ➔ {{ order.destination }}</strong>
              </div>
              <div>
                <span class="text-slate-400">承运司机:</span>
                <span class="font-mono text-slate-800 ml-1 font-bold">{{ order.driverName }} ({{ order.driverPhone }})</span>
              </div>
              <div class="col-span-full flex items-center justify-between text-[11px] pt-1 border-t border-slate-100">
                <span class="text-slate-500">当前节点: <strong class="text-brand-600">{{ order.currentNode }}</strong></span>
                <span class="text-slate-500">预计到达: <strong class="text-slate-800 font-mono">{{ order.eta }}</strong></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧单栏: 客户画像 & 实时通话便签记录 -->
      <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex flex-col justify-between">
        <div class="space-y-4 text-xs">
          <!-- 客户档案画像 -->
          <div>
            <h4 class="font-extrabold text-sm text-slate-900 mb-2">👤 客户服务画像</h4>
            <div class="p-3 bg-slate-50 rounded-2xl space-y-1.5 text-slate-600">
              <div class="flex justify-between">
                <span class="text-slate-400">历史呼入:</span>
                <strong class="font-mono text-slate-800">8 次 (接通率 100%)</strong>
              </div>
              <div class="flex justify-between">
                <span class="text-slate-400">平均满意度:</span>
                <strong class="text-amber-500 font-extrabold">★★★★★ 5.0</strong>
              </div>
              <div class="flex justify-between">
                <span class="text-slate-400">上次咨询:</span>
                <span class="text-slate-700">冷链温度监控数据查询</span>
              </div>
            </div>
          </div>

          <!-- 实时便签与快捷诉求勾选 -->
          <div>
            <h4 class="font-extrabold text-sm text-slate-900 mb-2">📝 通话跟进便签</h4>
            <!-- 快速诉求标签 -->
            <div class="flex flex-wrap gap-1.5 mb-2.5">
              <button
                v-for="tag in quickTags"
                :key="tag"
                @click="toggleTag(tag)"
                class="px-2.5 py-1 rounded-lg text-[11px] font-medium transition"
                :class="selectedTags.includes(tag) ? 'bg-brand-500 text-white font-bold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
              >
                {{ tag }}
              </button>
            </div>
            <textarea
              v-model="callNotes"
              rows="4"
              placeholder="边通话边记录客户诉求、约定跟进时间或核实信息..."
              class="w-full bg-slate-50 border border-slate-200 rounded-xl p-3 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:bg-white resize-none"
            ></textarea>
          </div>
        </div>

        <!-- 底部一键挂机并归档 -->
        <div class="pt-4 border-t border-slate-100">
          <button
            @click="handleHangupAndReturn"
            class="w-full py-3 bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 active:scale-98 text-white font-extrabold text-xs rounded-2xl shadow-pill transition flex items-center justify-center gap-2 cursor-pointer"
          >
            <span>💾</span>
            <span>保存记录并结束通话</span>
          </button>
        </div>
      </div>

    </div>

    <!-- 呼叫转接弹窗 (支持输入目标坐席工号或分机号) -->
    <div v-if="isTransferModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-2xl space-y-4 text-slate-800">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <span>↪️</span>
            <span>呼叫转接 / 盲转</span>
          </h3>
          <button @click="isTransferModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <p class="text-xs text-slate-500 leading-relaxed">
          将当前通话的客户（<span class="font-mono font-bold text-slate-800">{{ callStore.currentCall?.callerNumber || '客户通道' }}</span>）转接至目标坐席或通信分机，转接后本席位将自动释放返回待命状态。
        </p>

        <div v-if="transferError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-600 font-bold">
          {{ transferError }}
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-bold text-slate-700">目标坐席工号 / 分机号</label>
          <input
            v-model="transferTarget"
            type="text"
            placeholder="例如: 1017 或 90101"
            class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            @keyup.enter="handleTransferSubmit"
          />
        </div>

        <!-- 快捷选择目标分机 -->
        <div class="space-y-1.5">
          <div class="text-[11px] font-bold text-slate-400">快捷选择目标:</div>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="target in ['1017', '1007', '90101', '901001']"
              :key="target"
              type="button"
              @click="transferTarget = target"
              class="px-2.5 py-1 rounded-lg text-xs font-mono font-bold transition cursor-pointer border"
              :class="transferTarget === target ? 'bg-indigo-50 border-brand-300 text-brand-600 font-extrabold' : 'bg-slate-100 hover:bg-slate-200 text-slate-700 border-transparent'"
            >
              {{ target }}
            </button>
          </div>
        </div>

        <div class="pt-3 border-t border-slate-100 flex justify-end space-x-2">
          <button @click="isTransferModalOpen = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer">
            取消
          </button>
          <button
            @click="handleTransferSubmit"
            :disabled="transferring"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition disabled:opacity-50 cursor-pointer"
          >
            <span v-if="transferring">正在下发转接...</span>
            <span v-else>确认转接</span>
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useCallStore } from '../../stores/callStore';
import { useCdrStore } from '../../stores/cdrStore';
import { useAgentStore } from '../../stores/agentStore';
import { triggerTransferCall } from '../../api/telephonyApi';

const callStore = useCallStore();
const cdrStore = useCdrStore();
const agentStore = useAgentStore();

const searchKeyword = ref('');
const selectedOrderNo = ref('YD202609180092');
const callNotes = ref('');
const selectedTags = ref<string[]>(['运单催促']);

const quickTags = ['运单催促', '修改地址', '货物破损', '索要电子回单', '价格咨询'];

export interface OrderItem {
  orderNo: string;
  goodsType: string;
  status: string;
  statusText: string;
  createTime: string;
  origin: string;
  destination: string;
  driverName: string;
  driverPhone: string;
  currentNode: string;
  eta: string;
}

const orders = ref<OrderItem[]>([]);

const filteredOrders = computed(() => {
  if (!searchKeyword.value.trim()) return orders.value;
  const kw = searchKeyword.value.trim().toLowerCase();
  return orders.value.filter(
    (o) =>
      o.orderNo.toLowerCase().includes(kw) ||
      o.driverName.includes(kw) ||
      o.driverPhone.includes(kw) ||
      o.origin.includes(kw) ||
      o.destination.includes(kw)
  );
});

const formattedDuration = computed(() => {
  const sec = callStore.durationSeconds;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
});

function toggleTag(tag: string) {
  const idx = selectedTags.value.indexOf(tag);
  if (idx > -1) {
    selectedTags.value.splice(idx, 1);
  } else {
    selectedTags.value.push(tag);
  }
}

function refreshBusinessData() {
  searchKeyword.value = '';
}

// 核心规则：挂断之后，重新拉取后端真实话单并平滑回到原来的界面
function handleHangupAndReturn() {
  // 1. 话单事实由后端在挂机时写入 fcc_call_session，前端只重新拉取，不本地拼装记录
  cdrStore.loadRecords(1);

  // 2. 挂机并重置话务态，自然退回原所在界面 (未接待回拨 Table 或 话单列表)
  callStore.hangupCall();
  callStore.closeAcw();
}

// 呼叫转接弹窗状态与操作
const isTransferModalOpen = ref(false);
const transferTarget = ref('1017');
const transferring = ref(false);
const transferError = ref('');

function openTransferModal() {
  transferTarget.value = '1017';
  transferError.value = '';
  isTransferModalOpen.value = true;
}

async function handleTransferSubmit() {
  const target = transferTarget.value.trim();
  if (!target) {
    transferError.value = '请输入目标坐席工号或分机号';
    return;
  }
  transferring.value = true;
  transferError.value = '';
  try {
    const res = await triggerTransferCall(
      agentStore.workNo,
      target,
      callStore.currentCall?.callId
    );
    if (res.code === 200) {
      isTransferModalOpen.value = false;
      handleHangupAndReturn();
    } else {
      transferError.value = res.message || '转接失败';
    }
  } catch (err: any) {
    transferError.value = err.message || '转接信令下发异常';
  } finally {
    transferring.value = false;
  }
}
</script>
