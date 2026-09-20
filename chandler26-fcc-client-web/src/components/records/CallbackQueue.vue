<template>
  <div class="flex-1 flex flex-col bg-white rounded-3xl border border-slate-200/80 shadow-card p-4 sm:p-6 min-h-0">
    <!-- 头部与检索控制栏 -->
    <div class="flex flex-wrap items-center justify-between pb-4 border-b border-slate-100 gap-3 text-xs">
      <div>
        <div class="flex items-center gap-2.5">
          <h3 class="font-extrabold text-sm sm:text-base text-slate-900">未接待漏话待办总池</h3>
          <span class="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-bold text-xs">
            待回呼 {{ pendingCount }} 位
          </span>
        </div>
        <p class="text-xs text-slate-400 mt-0.5">高优先级跟进工单 · 呼入排队超时或放弃客户</p>
      </div>

      <!-- 搜索与状态筛选 -->
      <div class="flex flex-wrap items-center gap-2.5">
        <div class="flex items-center gap-1.5">
          <input
            v-model="searchPhone"
            @keyup.enter="loadTasks"
            type="text"
            placeholder="输入客户手机号检索..."
            class="w-40 sm:w-48 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-slate-800 font-mono text-xs focus:outline-none focus:border-brand-500 focus:bg-white transition"
          />
        </div>


        <button
          @click="loadTasks"
          class="px-4 py-1.5 bg-brand-500 hover:bg-brand-600 active:scale-95 text-white font-extrabold rounded-xl shadow-pill transition cursor-pointer"
        >
          查询
        </button>
        <button
          @click="resetFilter"
          class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 font-bold rounded-xl transition cursor-pointer"
        >
          重置
        </button>
      </div>
    </div>

    <!-- 漏话回拨专业数据表格 (Table) -->
    <div class="flex-1 overflow-x-auto mt-2">
      <table class="w-full text-xs text-left">
        <thead class="text-slate-400 border-b border-slate-100 pb-2 text-[11px] font-bold">
          <tr>
            <th class="py-3 px-3">序号/单号</th>
            <th class="py-3 px-3">客户号码</th>
            <th class="py-3 px-3">呼入DID</th>
            <th class="py-3 px-3">漏话时间</th>
            <th class="py-3 px-3">等待时长</th>
            <th class="py-3 px-3">漏话原因</th>
            <th class="py-3 px-3">回拨状态</th>
            <th class="py-3 px-3">跟进坐席</th>
            <th class="py-3 px-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody v-if="!isLoading && tasks.length > 0" class="divide-y divide-slate-50">
          <tr
            v-for="item in tasks"
            :key="item.id"
            class="hover:bg-slate-50/80 transition-colors"
          >
            <!-- 序号/单号 -->
            <td class="py-3.5 px-3 font-mono text-slate-500 font-bold">
              #{{ item.id }}
            </td>

            <!-- 客户/司机 -->
            <td class="py-3.5 px-3">
              <div class="font-mono text-slate-600 text-[11px] mt-0.5">
                {{ item.phone }}
              </div>
            </td>

            <!-- 呼入DID -->
            <td class="py-3.5 px-3 font-mono text-slate-600">
              {{ item.didNumber || '-' }}
            </td>

            <!-- 漏话时间 -->
            <td class="py-3.5 px-3 font-mono text-slate-600">
              {{ item.time }}
            </td>

            <!-- 等待时长 -->
            <td class="py-3.5 px-3 font-mono font-bold text-rose-600">
              {{ item.duration }}
            </td>

            <!-- 漏话原因 -->
            <td class="py-3.5 px-3">
              <span
                class="px-2 py-0.5 rounded-md text-[10px] font-bold"
                :class="getReasonTagClass(item.reason)"
              >
                {{ item.reason }}
              </span>
            </td>

            <!-- 回拨状态 -->
            <td class="py-3.5 px-3">
              <span
                class="px-2 py-0.5 rounded-full text-[11px] font-extrabold"
                :class="getStatusTagClass(item.status)"
              >
                {{ getStatusText(item.status) }}
              </span>
            </td>

            <!-- 跟进坐席 -->
            <td class="py-3.5 px-3 text-slate-700">
              <span v-if="item.assignee" class="font-medium">
                {{ item.assignee }} <span class="text-slate-400 font-mono text-[10px]">({{ item.assigneeWorkNo }})</span>
              </span>
              <span v-else class="text-slate-300 font-medium">待分配</span>
            </td>

            <!-- 操作列: 立即回拨 -->
            <td class="py-3.5 px-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <button
                  @click="handleTriggerCall(item)"
                  :disabled="!!callingId || ['RUNNING', 'PAUSED', 'SUCCEEDED'].includes(item.status)"
                  class="px-3.5 py-1 rounded-full bg-brand-500 hover:bg-brand-600 active:scale-95 text-white font-extrabold text-xs transition shadow-pill flex items-center gap-1 cursor-pointer"
                >
                  <span>📞</span>
                  <span>{{ callingId === item.id ? '正在安排…' : '安排回拨' }}</span>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 加载状态 -->
      <div v-if="isLoading" class="py-16 text-center text-slate-400">
        <div class="w-8 h-8 rounded-full border-2 border-brand-500 border-t-transparent animate-spin mx-auto mb-2"></div>
        <p class="text-xs">正在查询未接待漏话待办...</p>
      </div>

      <!-- 空状态 -->
      <div v-if="!isLoading && tasks.length === 0" class="py-16 text-center text-slate-400">
        <div class="text-3xl mb-2">🎉</div>
        <p class="text-xs font-bold text-slate-600">暂无待处理的漏话回拨任务</p>
        <p class="text-[11px] text-slate-400 mt-1">呼入电话均已全部接待妥当。</p>
      </div>
    </div>

    <!-- 底部统计与分页 -->
    <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
      <span>共 {{ total }} 条待跟进任务记录</span>
      <div class="flex items-center gap-1.5 font-mono">
        <button
          @click="page > 1 && (page--, loadTasks())"
          :disabled="page <= 1"
          class="w-7 h-7 rounded-xl border border-slate-200 flex items-center justify-center disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
        >
          &lt;
        </button>
        <span class="px-2 font-bold text-slate-800">{{ page }}</span>
        <button
          @click="page * 10 < total && (page++, loadTasks())"
          :disabled="page * 10 >= total"
          class="w-7 h-7 rounded-xl border border-slate-200 flex items-center justify-center disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
        >
          &gt;
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { callbackApi, type CallbackTaskVO } from '../../api/callbackApi';
import { toast, toastError } from '../../utils/feedback';
const callingId = ref('');

const searchPhone = ref('');
const page = ref(1);
const total = ref(0);
const isLoading = ref(false);

const tasks = ref<CallbackTaskVO[]>([]);

const pendingCount = computed(() => {
  return tasks.value.filter((t) => t.status === 'PENDING').length;
});

onMounted(() => {
  loadTasks();
});

async function loadTasks() {
  isLoading.value = true;
  try {
    const res = await callbackApi.list({
      pageNum: page.value,
      pageSize: 10,
      customerNumber: searchPhone.value.trim() || undefined,
    });
    if (res && res.list) {
      tasks.value = res.list;
      total.value = res.total != null ? res.total : res.list.length;
    } else {
      tasks.value = [];
      total.value = 0;
    }
  } catch (e) {
    toastError(e instanceof Error ? e.message : '回拨列表加载失败，请重试');
    tasks.value = [];
    total.value = 0;
  } finally {
    isLoading.value = false;
  }
}

function resetFilter() {
  searchPhone.value = '';
  page.value = 1;
  loadTasks();
}

async function handleTriggerCall(item: CallbackTaskVO) {
  if (callingId.value) return;
  callingId.value = item.id;
  try {
    await callbackApi.call(item.id);
    toast('已安排回拨，请保持就绪；调度器将在允许时段执行，可在自动外呼查看结果');
    await loadTasks();
  } catch (error) {
    toastError(error instanceof Error ? error.message : '安排回拨失败');
  } finally { callingId.value = ''; }
}

function getReasonTagClass(reason?: string): string {
  if (!reason) return 'bg-slate-100 text-slate-700';
  if (reason.includes('忙') || reason.includes('全忙')) return 'bg-rose-50 text-rose-700 border border-rose-200';
  if (reason.includes('超时')) return 'bg-amber-50 text-amber-700 border border-amber-200';
  return 'bg-blue-50 text-blue-700 border border-blue-200';
}

function getStatusTagClass(status: string): string {
  if (status === 'PENDING') return 'bg-rose-50 text-rose-700 border border-rose-200';
  if (status === 'ASSIGNED') return 'bg-amber-50 text-amber-700 border border-amber-200';
  return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
}

function getStatusText(status: string): string {
  if (status === 'PENDING') return '待回拨';
  if (status === 'ASSIGNED') return '已派单';
  return ({ SCHEDULED: '已安排', RUNNING: '执行中', PAUSED: '已暂停', SUCCEEDED: '已接通', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[status] || status;
}
</script>
