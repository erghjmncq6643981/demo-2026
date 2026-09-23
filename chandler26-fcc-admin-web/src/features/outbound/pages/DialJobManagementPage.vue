<script setup lang="ts">
import {
  ListRestart,
  Pause,
  PhoneOutgoing,
  Play,
  Plus,
  RefreshCw,
  Search,
  X,
} from "lucide-vue-next";
import { useDialJobManagement } from "../composables/useDialJobManagement";

const state = useDialJobManagement();
const statusConfig: Record<string, { label: string; class: string }> = {
  PENDING: { label: "待调度", class: "bg-blue-50 text-blue-700 border-blue-200" },
  RUNNING: { label: "执行中", class: "bg-amber-50 text-amber-700 border-amber-200" },
  PAUSED: { label: "已暂停", class: "bg-slate-100 text-slate-600 border-slate-200" },
  SUCCEEDED: { label: "已完成", class: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  FAILED: { label: "失败", class: "bg-rose-50 text-rose-700 border-rose-200" },
  CANCELLED: { label: "已取消", class: "bg-slate-100 text-slate-400 border-slate-200" },
};
</script>

<template>
  <div class="space-y-6">
    <!-- Header Card -->
    <div
      class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card"
    >
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <PhoneOutgoing class="w-5 h-5 text-brand-600" />
          自动外呼管理
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          管理持久任务、调度状态及逐次外呼结果
        </p>
      </div>

      <div class="flex items-center space-x-3">
        <button
          title="刷新任务列表"
          class="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl transition border border-slate-200 cursor-pointer disabled:opacity-40"
          :disabled="state.loading.value"
          @click="state.load"
        >
          <RefreshCw
            class="w-4 h-4"
            :class="{ 'animate-spin': state.loading.value }"
          />
        </button>
        <button
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
          @click="state.openCreate"
        >
          <Plus class="w-4 h-4" />
          <span>新建任务</span>
        </button>
      </div>
    </div>

    <!-- Filter Card -->
    <div
      class="flex flex-wrap items-end gap-3 text-sm bg-white p-4 rounded-3xl border border-slate-100 shadow-card"
    >
      <div class="w-72">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">自动外呼流程</label>
        <select
          v-model="state.flowFilter.value"
          :disabled="state.flowsLoading.value"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
        >
          <option value="">
            {{ state.flowsLoading.value ? "流程加载中..." : "全部流程" }}
          </option>
          <option
            v-for="flow in state.flows.value"
            :key="flow.id"
            :value="flow.flowKey"
          >
            {{ flow.flowName }} · {{ flow.flowKey }}
          </option>
        </select>
      </div>

      <button
        class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        @click="state.search"
      >
        <Search class="w-3.5 h-3.5" />
        <span>查询</span>
      </button>

      <span v-if="state.flowsError.value" class="text-xs text-rose-600 flex items-center gap-1 ml-2">
        {{ state.flowsError.value }}
        <button class="font-bold underline cursor-pointer" @click="state.loadFlows">
          重试
        </button>
      </span>
    </div>

    <!-- Error Alert -->
    <div
      v-if="state.error.value"
      role="alert"
      class="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-between text-xs text-rose-700 font-bold"
    >
      <span>{{ state.error.value }}</span>
      <button class="font-bold underline cursor-pointer" @click="state.load">
        重试
      </button>
    </div>

    <!-- Table Card -->
    <div
      class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr
              class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold"
            >
              <th class="py-3.5 px-4">任务编号 / 尝试限制</th>
              <th class="py-3.5 px-4">被叫号码</th>
              <th class="py-3.5 px-4">流程模型</th>
              <th class="py-3.5 px-4">创建人</th>
              <th class="py-3.5 px-4">任务状态</th>
              <th class="py-3.5 px-4">计划时间</th>
              <th class="py-3.5 px-4 text-right">调度控制</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="state.loading.value">
              <td colspan="7" class="py-12 text-center text-slate-400">
                <RefreshCw
                  class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500"
                />
                正在加载自动外呼任务...
              </td>
            </tr>
            <tr v-else-if="state.rows.value.length === 0">
              <td colspan="7" class="py-12 text-center text-slate-400 font-medium">
                <PhoneOutgoing class="w-8 h-8 mx-auto mb-2 text-slate-300" />
                暂无自动外呼任务
              </td>
            </tr>
            <tr
              v-for="row in state.rows.value"
              v-else
              :key="row.id"
              class="hover:bg-slate-50/80 transition-colors"
            >
              <td class="py-4 px-4">
                <div class="font-mono font-bold text-slate-900" :title="row.id">
                  {{ row.id }}
                </div>
                <div class="text-xs text-slate-400 font-medium">
                  最多 {{ row.maxAttempts }} 次尝试
                </div>
              </td>
              <td class="py-4 px-4 font-mono text-xs font-bold text-slate-800">
                {{ row.number }}
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2.5 py-0.5 rounded-lg font-mono text-xs bg-slate-100 text-slate-800 border border-slate-200"
                >
                  {{ row.flowKey }}
                </span>
              </td>
              <td class="py-4 px-4">
                <span class="text-xs font-semibold text-slate-700">
                  {{ row.createdBy }}
                </span>
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2.5 py-0.5 rounded-full text-xs font-bold border"
                  :class="statusConfig[row.status]?.class || 'bg-slate-100 text-slate-700 border-slate-200'"
                >
                  {{ statusConfig[row.status]?.label || row.status }}
                </span>
              </td>
              <td class="py-4 px-4 font-mono text-xs text-slate-500">
                {{ row.scheduledAt || "-" }}
              </td>
              <td class="py-4 px-4 text-right">
                <div class="inline-flex items-center gap-1.5">
                  <button
                    title="查看尝试记录"
                    class="px-2.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 border border-brand-200 rounded-xl text-xs font-bold inline-flex items-center gap-1 transition cursor-pointer"
                    @click="state.showAttempts(row)"
                  >
                    <ListRestart class="w-3.5 h-3.5" />
                    <span>记录</span>
                  </button>

                  <button
                    v-if="row.status === 'PENDING'"
                    title="暂停任务"
                    class="px-2.5 py-1.5 bg-amber-50 hover:bg-amber-100 text-amber-700 border border-amber-200 rounded-xl text-xs font-bold inline-flex items-center gap-1 transition cursor-pointer disabled:opacity-40"
                    :disabled="state.controllingId.value === row.id"
                    @click="state.control(row, 'PAUSE')"
                  >
                    <Pause class="w-3.5 h-3.5" />
                    <span>暂停</span>
                  </button>

                  <button
                    v-if="row.status === 'PAUSED'"
                    title="恢复任务"
                    class="px-2.5 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-xl text-xs font-bold inline-flex items-center gap-1 transition cursor-pointer disabled:opacity-40"
                    :disabled="state.controllingId.value === row.id"
                    @click="state.control(row, 'RESUME')"
                  >
                    <Play class="w-3.5 h-3.5" />
                    <span>恢复</span>
                  </button>

                  <button
                    v-if="['PENDING', 'PAUSED', 'RUNNING'].includes(row.status)"
                    title="取消任务"
                    class="px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-xl text-xs font-bold inline-flex items-center gap-1 transition cursor-pointer disabled:opacity-40"
                    :disabled="state.controllingId.value === row.id"
                    @click="state.control(row, 'CANCEL')"
                  >
                    <X class="w-3.5 h-3.5" />
                    <span>取消</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Footer -->
      <div
        class="mt-5 flex items-center justify-between text-xs text-slate-500 pt-3 border-t border-slate-100"
      >
        <span class="font-medium">第 {{ state.page.value }} 页</span>
        <div class="flex items-center gap-2">
          <button
            class="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 disabled:opacity-40 disabled:cursor-not-allowed font-bold transition cursor-pointer"
            :disabled="state.page.value === 1"
            @click="state.previous"
          >
            上一页
          </button>
          <button
            class="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 disabled:opacity-40 disabled:cursor-not-allowed font-bold transition cursor-pointer"
            :disabled="!state.hasNext.value"
            @click="state.next"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- Create Task Dialog -->
    <el-dialog
      v-model="state.createVisible.value"
      title="新建自动外呼任务"
      width="min(34rem, calc(100vw - 2rem))"
      append-to-body
      class="rounded-3xl"
    >
      <form
        class="grid grid-cols-1 gap-4 sm:grid-cols-2 p-1"
        @submit.prevent="state.create"
      >
        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >自动外呼流程 <span class="text-rose-500">*</span></label
          >
          <select
            v-model="state.form.value.flowKey"
            :disabled="state.flowsLoading.value"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
          >
            <option value="">
              {{ state.flowsLoading.value ? "流程加载中..." : "请选择已发布流程" }}
            </option>
            <option
              v-for="flow in state.flows.value"
              :key="flow.id"
              :value="flow.flowKey"
            >
              {{ flow.flowName }} · {{ flow.flowKey }}
            </option>
          </select>
          <span v-if="state.flowsError.value" class="text-xs text-rose-600 flex items-center gap-1 mt-1">
            {{ state.flowsError.value }}
            <button class="font-bold underline cursor-pointer" @click="state.loadFlows">
              重试
            </button>
          </span>
        </div>

        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >被叫号码 <span class="text-rose-500">*</span></label
          >
          <input
            v-model="state.form.value.number"
            placeholder="输入外呼被叫号码"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium font-mono"
          />
        </div>

        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >播报文案 <span class="text-rose-500">*</span></label
          >
          <textarea
            v-model="state.form.value.variables.text"
            rows="4"
            placeholder="输入客户接通后播放的文案，服务端将其作为流程变量交给 Sidecar TTS"
            class="w-full resize-y bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium"
          />
        </div>

        <div>
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >确认按键</label
          >
          <select
            v-model="state.form.value.variables.confirmDigit"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
          >
            <option v-for="digit in ['0','1','2','3','4','5','6','7','8','9']" :key="digit" :value="digit">
              按 {{ digit }} 确认
            </option>
          </select>
        </div>

        <div>
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >最多尝试次数</label
          >
          <select
            v-model="state.form.value.maxAttempts"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
          >
            <option :value="1">1 次</option>
            <option :value="2">2 次</option>
            <option :value="3">3 次</option>
          </select>
        </div>

        <p class="sm:col-span-2 text-xs text-slate-400 bg-slate-50 p-3 rounded-xl border border-slate-100">
          任务只拨打客户并执行所选流程，与坐席无关；只有流程进入“转人工”节点时才动态选择坐席。
        </p>

        <div class="sm:col-span-2 flex justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            type="button"
            class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer"
            @click="state.createVisible.value = false"
          >
            取消
          </button>
          <button
            type="submit"
            class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition cursor-pointer disabled:opacity-50"
            :disabled="state.saving.value"
          >
            {{ state.saving.value ? "创建中..." : "创建任务" }}
          </button>
        </div>
      </form>
    </el-dialog>

    <!-- Attempt Records Dialog -->
    <el-dialog
      v-model="state.attemptsVisible.value"
      :title="`任务执行记录 · ${state.selectedJob.value?.id || ''}`"
      width="min(46rem, calc(100vw - 2rem))"
      append-to-body
      class="rounded-3xl"
    >
      <div v-if="state.attemptsLoading.value" class="py-12 text-center text-slate-400">
        <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
        正在加载执行记录...
      </div>
      <div v-else-if="state.attempts.value.length === 0" class="py-12 text-center text-slate-400 font-medium">
        <ListRestart class="w-8 h-8 mx-auto mb-2 text-slate-300" />
        该任务尚未产生呼叫尝试
      </div>
      <div v-else class="space-y-3 p-1 max-h-[60vh] overflow-y-auto">
        <div
          v-for="attempt in state.attempts.value"
          :key="attempt.id"
          class="p-4 rounded-2xl bg-slate-50/80 border border-slate-100 space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="font-bold text-slate-900 text-xs">
              第 {{ attempt.attemptNo }} 次尝试
            </span>
            <span
              class="px-2.5 py-0.5 rounded-full text-xs font-bold border"
              :class="statusConfig[attempt.status]?.class || 'bg-slate-100 text-slate-700 border-slate-200'"
            >
              {{ statusConfig[attempt.status]?.label || attempt.status }}
            </span>
          </div>

          <div class="grid grid-cols-2 gap-2 text-xs">
            <div>
              <span class="text-slate-400">执行结果：</span>
              <span class="font-semibold text-slate-700">{{ attempt.result || "等待结果" }}</span>
            </div>
            <div>
              <span class="text-slate-400">通话 ID：</span>
              <span class="font-mono text-slate-700 truncate" :title="attempt.callId">{{ attempt.callId || "-" }}</span>
            </div>
          </div>

          <div class="text-xs text-slate-400 font-mono flex items-center justify-between pt-1 border-t border-slate-200/60">
            <span>{{ attempt.startedAt || "-" }} → {{ attempt.endedAt || "未结束" }}</span>
            <span v-if="attempt.failureReason" class="text-rose-500 font-sans font-bold">
              {{ attempt.failureReason }}
            </span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
