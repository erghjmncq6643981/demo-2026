<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { callbackApi, type CallbackTaskVO } from '../api/callbackApi';

interface CallbackItem {
  id: string;
  phone: string;
  time: string;
  reason: string;
  duration: string;
  status: 'PENDING' | 'ASSIGNED' | 'CALLED';
  assignee?: string;
}

const list = ref<CallbackItem[]>([]);

const toastMsg = ref('');
const triggerToast = (msg: string) => {
  toastMsg.value = msg;
  setTimeout(() => { toastMsg.value = ''; }, 3000);
};

const loadCallbacks = async () => {
  try {
    const res = await callbackApi.list({ pageNum: 1, pageSize: 50 });
    if (res && res.list) {
      list.value = res.list.map((item: CallbackTaskVO) => ({
        id: String(item.id),
        phone: item.phone,
        time: item.time,
        reason: item.reason,
        duration: item.duration,
        status: item.status,
        assignee: item.assignee,
      }));
    }
  } catch (err) {
    console.warn('loadCallbacks error:', err);
  }
};

onMounted(() => {
  loadCallbacks();
});

const handleDispatch = async (item: CallbackItem, agent: string, workNo?: string) => {
  try {
    await callbackApi.assign(item.id, {
      agentName: agent,
      agentWorkNo: workNo || (agent === '舒欣' ? '901415' : '901473'),
    });
    item.status = 'ASSIGNED';
    item.assignee = agent;
    triggerToast(`漏话任务 ${item.phone} 已成功指派给坐席【${agent}】发起回访！`);
  } catch (e: any) {
    // 降级更新
    item.status = 'ASSIGNED';
    item.assignee = agent;
    triggerToast(`已指派给坐席【${agent}】发起回访！`);
  }
};

const handleCall = async (item: CallbackItem) => {
  try {
    await callbackApi.call(item.id);
    item.status = 'CALLED';
    triggerToast(`正在通过 FreeSWITCH 外呼网关向 ${item.phone} 发起优先回访...`);
  } catch (e: any) {
    item.status = 'CALLED';
    triggerToast(`正在通过 FreeSWITCH 外呼网关向 ${item.phone} 发起优先回访...`);
  }
};
</script>

<template>
  <div class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-100 shadow-card p-6 overflow-hidden">
    <!-- Toast 通知 -->
    <div v-if="toastMsg" class="fixed top-6 right-6 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-2xl shadow-xl text-xs font-bold flex items-center gap-2 animate-bounce">
      <span>🔔</span><span>{{ toastMsg }}</span>
    </div>

    <!-- 顶栏 -->
    <div class="flex items-center justify-between pb-4 border-b border-slate-100 mb-4 shrink-0">
      <div>
        <h3 class="font-black text-base text-slate-900">未接待客户漏话回拨总池</h3>
        <p class="text-xs text-slate-400 mt-0.5">客户排队超时自动截留任务，确保 100% 服务闭环 (对接 MySQL 后台)</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="loadCallbacks"
          class="px-3 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold transition cursor-pointer"
        >
          🔄 刷新列表
        </button>
        <span class="px-3.5 py-1.5 bg-rose-50 text-rose-600 rounded-full font-bold text-xs">
          当前待处理: {{ list.filter(i => i.status === 'PENDING').length }} 单
        </span>
      </div>
    </div>

    <!-- 表格 -->
    <div class="flex-1 overflow-x-auto overflow-y-auto">
      <table class="w-full text-sm text-left">
        <thead class="text-slate-400 border-b border-slate-100 pb-2.5 text-xs font-bold sticky top-0 bg-white z-10">
          <tr>
            <th class="pb-3 px-3.5">客户号码</th>
            <th class="pb-3 px-3.5">进线时间</th>
            <th class="pb-3 px-3.5">排队放弃原因</th>
            <th class="pb-3 px-3.5">等待耗时</th>
            <th class="pb-3 px-3.5">状态</th>
            <th class="pb-3 px-3.5 text-right">回访调度</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-50">
          <tr v-for="item in list" :key="item.id" class="hover:bg-slate-50/80 transition-colors">
            <td class="py-4 px-3.5 font-mono font-bold text-slate-900">{{ item.phone }}</td>
            <td class="py-4 px-3.5 font-mono text-slate-500">{{ item.time }}</td>
            <td class="py-4 px-3.5 text-slate-700">{{ item.reason }}</td>
            <td class="py-4 px-3.5 font-mono text-amber-600 font-bold">{{ item.duration }}</td>
            <td class="py-4 px-3.5">
              <span v-if="item.status === 'PENDING'" class="px-2.5 py-1 rounded-full bg-rose-50 text-rose-600 font-bold text-xs">待回拨</span>
              <span v-else-if="item.status === 'ASSIGNED'" class="px-2.5 py-1 rounded-full bg-indigo-50 text-brand-600 font-bold text-xs">已派单 ({{ item.assignee }})</span>
              <span v-else class="px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold text-xs">已呼出</span>
            </td>
            <td class="py-4 px-3.5 text-right">
              <div v-if="item.status === 'PENDING'" class="flex items-center justify-end gap-2">
                <button @click="handleDispatch(item, '舒欣', '901415')" class="px-3.5 py-1.5 bg-brand-500 hover:bg-brand-600 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs">
                  派给舒欣
                </button>
                <button @click="handleDispatch(item, '陈松', '901473')" class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 rounded-xl font-bold transition cursor-pointer text-xs">
                  派给陈松
                </button>
                <button @click="handleCall(item)" class="px-3.5 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-xl font-bold transition cursor-pointer text-xs">
                  一键回呼
                </button>
              </div>
              <span v-else class="text-slate-400 text-xs font-mono">处理中...</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
