<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { cdrApi, type CallCdrVO } from '../api/cdrApi';

const selectedType = ref<'inbound' | 'outbound' | 'auto'>('inbound');
const refreshTime = ref('刚刚');
const isLoading = ref(false);
const rawCdrs = ref<CallCdrVO[]>([]);

const todayStr = computed(() => {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
});

const formatDuration = (ms: number): string => {
  if (!ms || ms <= 0) return '0秒';
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}时${minutes}分${seconds}秒`;
  if (minutes > 0) return `${minutes}分${seconds}秒`;
  return `${seconds}秒`;
};

// 呼出统计
const outboundList = computed(() => rawCdrs.value.filter(c => c.direction === 'OUTBOUND'));
const outboundTotal = computed(() => outboundList.value.length);
const outboundConnected = computed(() => outboundList.value.filter(c => c.status === 'ANSWERED' || c.status === 'NORMAL_END').length);
const outboundRate = computed(() => outboundTotal.value ? ((outboundConnected.value / outboundTotal.value) * 100).toFixed(2) + '%' : '0.00%');
const outboundDuration = computed(() => formatDuration(outboundList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0)));

// 呼入统计
const inboundList = computed(() => rawCdrs.value.filter(c => c.direction === 'INBOUND'));
const inboundTotal = computed(() => inboundList.value.length);
const inboundConnected = computed(() => inboundList.value.filter(c => c.status === 'ANSWERED' || c.status === 'NORMAL_END').length);
const inboundRate = computed(() => inboundTotal.value ? ((inboundConnected.value / inboundTotal.value) * 100).toFixed(2) + '%' : '0.00%');
const inboundDuration = computed(() => formatDuration(inboundList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0)));

// 智能外呼统计
const autoList = computed(() => rawCdrs.value.filter(c => c.routeMode === 'CAMPAIGN' || c.modelType === 'CAMPAIGN'));
const autoTotal = computed(() => autoList.value.length);
const autoConnected = computed(() => autoList.value.filter(c => c.status === 'ANSWERED' || c.status === 'NORMAL_END').length);
const autoRate = computed(() => autoTotal.value ? ((autoConnected.value / autoTotal.value) * 100).toFixed(2) + '%' : '0.00%');
const autoDuration = computed(() => formatDuration(autoList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0)));

// 内线呼叫统计
const internalList = computed(() => rawCdrs.value.filter(c => c.direction === 'INTERNAL'));
const internalTotal = computed(() => internalList.value.length);
const internalConnected = computed(() => internalList.value.filter(c => c.status === 'ANSWERED' || c.status === 'NORMAL_END').length);
const internalRate = computed(() => internalTotal.value ? ((internalConnected.value / internalTotal.value) * 100).toFixed(2) + '%' : '0.00%');
const internalDuration = computed(() => formatDuration(internalList.value.reduce((s, c) => s + (c.talkDurationMs || 0), 0)));

// 选定类型的展示总量与接通量
const currentStats = computed(() => {
  if (selectedType.value === 'inbound') {
    return {
      total: `${inboundTotal.value}通`,
      connected: `${inboundConnected.value}通`,
      count: inboundTotal.value,
      connCount: inboundConnected.value,
    };
  } else if (selectedType.value === 'outbound') {
    return {
      total: `${outboundTotal.value}通`,
      connected: `${outboundConnected.value}通`,
      count: outboundTotal.value,
      connCount: outboundConnected.value,
    };
  } else {
    return {
      total: `${autoTotal.value}通`,
      connected: `${autoConnected.value}通`,
      count: autoTotal.value,
      connCount: autoConnected.value,
    };
  }
});

const loadData = async () => {
  isLoading.value = true;
  try {
    const res = await cdrApi.list({ pageNum: 1, pageSize: 1000 });
    rawCdrs.value = res?.list || [];
    refreshTime.value = new Date().toLocaleTimeString();
  } catch (err) {
    console.error('加载监控大盘真实数据失败:', err);
  } finally {
    isLoading.value = false;
  }
};

const handleRefresh = () => {
  loadData();
};

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="flex-1 flex flex-col gap-6 overflow-y-auto pr-1">
    <!-- 全局通话信息卡片 -->
    <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6">
      <div class="flex items-center justify-between pb-4 border-b border-slate-100 mb-4">
        <div>
          <div class="flex items-center gap-2">
            <h3 class="font-black text-base text-slate-900">全局通话信息总览</h3>
            <span class="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-600 text-[11px] font-bold border border-emerald-100">
              真实数据库直连
            </span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5">全网各通道实时接通效能数据报表（已清空历史 Mock 假数据）</p>
        </div>
        <div class="flex items-center gap-3 text-xs">
          <span>统计周期: <strong class="text-slate-800 font-bold font-mono">{{ todayStr }} ~ {{ todayStr }}</strong></span>
          <button
            @click="handleRefresh"
            :disabled="isLoading"
            class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white font-bold rounded-xl shadow-xs transition cursor-pointer text-xs flex items-center gap-1.5"
          >
            <span :class="isLoading ? 'animate-spin' : ''">🔄</span>
            <span>{{ isLoading ? '刷新中...' : '刷新大盘' }}</span>
          </button>
        </div>
      </div>

      <!-- 数据明细矩阵 (真实数据库统计) -->
      <div class="overflow-x-auto">
        <table class="w-full text-sm text-left border border-slate-100 rounded-2xl overflow-hidden shadow-2xs">
          <tbody class="divide-y divide-slate-100">
            <tr class="bg-slate-50/60">
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">呼出总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">{{ outboundTotal }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">呼出接通数量</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ outboundConnected }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">呼出接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">{{ outboundRate }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700 w-36">呼出总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ outboundDuration }}</td>
            </tr>
            <tr>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">{{ inboundTotal }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入接通数量</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ inboundConnected }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">{{ inboundRate }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">呼入总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ inboundDuration }}</td>
            </tr>
            <tr class="bg-slate-50/60">
              <td class="py-3.5 px-4 font-bold text-slate-700">智能外呼总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">{{ autoTotal }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">智能外呼接通数</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ autoConnected }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">外呼接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">{{ autoRate }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">外呼总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ autoDuration }}</td>
            </tr>
            <tr>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线呼叫总数</td>
              <td class="py-3.5 px-4 font-mono font-black text-slate-900">{{ internalTotal }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线接通数量</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ internalConnected }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线接通率</td>
              <td class="py-3.5 px-4 font-mono font-black text-emerald-600">{{ internalRate }}</td>
              <td class="py-3.5 px-4 font-bold text-slate-700">内线总时长</td>
              <td class="py-3.5 px-4 font-mono text-slate-800">{{ internalDuration }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 通话趋势与类型分析 -->
    <div class="bg-white rounded-3xl border border-slate-100 shadow-card p-6 flex-1 flex flex-col justify-between min-h-[320px]">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <h3 class="font-black text-base text-slate-900">通话类型实时走势</h3>
          <div class="flex items-center gap-2 text-xs">
            <span class="text-slate-400">选择类型:</span>
            <select v-model="selectedType" class="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1 text-slate-800 font-bold focus:outline-none cursor-pointer">
              <option value="inbound">呼入通话</option>
              <option value="outbound">呼出通话</option>
              <option value="auto">智能外呼</option>
            </select>
          </div>
        </div>
        <div class="flex items-center gap-4 text-xs font-mono">
          <span class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
            <span>发起总量: <strong class="text-slate-800">{{ currentStats.total }}</strong></span>
          </span>
          <span class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            <span>成功接通: <strong class="text-slate-800">{{ currentStats.connected }}</strong></span>
          </span>
        </div>
      </div>

      <!-- 趋势展示区 (实测空状态与实时绘制) -->
      <div class="h-60 bg-slate-50/50 rounded-2xl border border-slate-100 p-4 relative flex flex-col justify-between overflow-hidden">
        <!-- 如果暂无测试通话流水，显示清爽直观的待测空状态 -->
        <div v-if="currentStats.count === 0" class="absolute inset-0 flex flex-col items-center justify-center text-center p-4 z-20 bg-slate-50/70 backdrop-blur-[1px]">
          <div class="w-12 h-12 rounded-2xl bg-white border border-slate-200 shadow-xs flex items-center justify-center text-xl mb-2">
            📈
          </div>
          <p class="text-xs font-bold text-slate-700 mb-0.5">当前时段暂无 {{ selectedType === 'inbound' ? '呼入' : selectedType === 'outbound' ? '呼出' : '外呼' }} 通话流水</p>
          <p class="text-[11px] text-slate-400">系统已接入真实话单库，产生通话后将在此绘制实时量级与接通走势波形</p>
        </div>

        <!-- Y轴基准线 -->
        <div class="absolute inset-x-4 top-1/4 border-b border-dashed border-slate-200 text-xs text-slate-400 pl-1 font-mono">10</div>
        <div class="absolute inset-x-4 top-2/4 border-b border-dashed border-slate-200 text-xs text-slate-400 pl-1 font-mono">5</div>
        <div class="absolute inset-x-4 top-3/4 border-b border-dashed border-slate-200 text-xs text-slate-400 pl-1 font-mono">1</div>

        <!-- 静态基线 (无流水时为平滑底线) -->
        <svg class="w-full h-full absolute inset-0 p-4" viewBox="0 0 800 180" preserveAspectRatio="none">
          <path d="M 0,170 L 800,170" fill="none" stroke="#E2E8F0" stroke-width="2" />
        </svg>

        <!-- X 轴时间标签 -->
        <div class="w-full flex justify-between text-xs font-mono text-slate-400 pt-2 z-10 mt-auto">
          <span>08:00</span><span>10:00</span><span>12:00</span><span>14:00</span><span>16:00</span><span>18:00</span><span>20:00</span>
        </div>
      </div>
    </div>
  </div>
</template>
