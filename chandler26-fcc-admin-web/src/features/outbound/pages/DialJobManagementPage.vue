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
const statusLabel: Record<string, string> = {
  PENDING: "待调度",
  RUNNING: "执行中",
  PAUSED: "已暂停",
  SUCCEEDED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
};
const modeLabel: Record<string, string> = {
  PROGRESSIVE: "坐席先接",
  NOTIFICATION: "通知外呼",
};
</script>

<template>
  <section class="h-full min-w-0 overflow-y-auto">
    <header class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-slate-900">自动外呼</h2>
        <p class="mt-1 text-sm text-slate-500">
          管理持久任务、调度状态和每次呼叫结果。
        </p>
      </div>
      <div class="flex gap-2">
        <button
          title="刷新任务"
          class="icon-button"
          :disabled="state.loading.value"
          @click="state.load"
        >
          <RefreshCw
            class="h-4 w-4"
            :class="state.loading.value ? 'animate-spin' : ''"
          /></button
        ><button class="primary-button" @click="state.openCreate">
          <Plus class="h-4 w-4" />新建任务
        </button>
      </div>
    </header>

    <div
      class="mb-4 flex flex-wrap items-end gap-3 border-y border-slate-200 bg-white px-4 py-3"
    >
      <label class="field"
        ><span>执行坐席</span
        ><select
          v-model="state.ownerFilter.value"
          :disabled="state.agentsLoading.value"
        >
          <option value="">
            {{ state.agentsLoading.value ? "坐席加载中..." : "全部坐席" }}
          </option>
          <option
            v-for="agent in state.agents.value"
            :key="agent.id"
            :value="agent.workNo"
          >
            {{ agent.workNo }} ·
            {{ agent.agentName || agent.realName || "未命名" }}
          </option>
        </select></label
      >
      <button class="secondary-button" @click="state.search">
        <Search class="h-4 w-4" />查询
      </button>
      <span v-if="state.agentsError.value" class="text-xs text-rose-600">
        {{ state.agentsError.value }}
        <button class="font-semibold underline" @click="state.loadAgents">
          重试
        </button>
      </span>
    </div>

    <div
      v-if="state.error.value"
      role="alert"
      class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
    >
      {{ state.error.value }}
      <button class="ml-2 font-semibold underline" @click="state.load">
        重试
      </button>
    </div>

    <div class="overflow-x-auto border border-slate-200 bg-white">
      <table class="w-full min-w-[980px] text-left text-sm">
        <thead class="bg-slate-50 text-xs text-slate-500">
          <tr>
            <th>任务</th>
            <th>被叫号码</th>
            <th>坐席</th>
            <th>模式</th>
            <th>状态</th>
            <th>计划时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="state.loading.value">
            <td colspan="7" class="empty">正在加载自动外呼任务...</td>
          </tr>
          <tr v-else-if="state.rows.value.length === 0">
            <td colspan="7" class="empty">
              <PhoneOutgoing
                class="mx-auto mb-2 h-6 w-6 text-slate-300"
              />暂无自动外呼任务
            </td>
          </tr>
          <tr v-for="row in state.rows.value" v-else :key="row.id">
            <td>
              <div
                class="font-mono font-semibold text-slate-900"
                :title="row.id"
              >
                {{ row.id }}
              </div>
              <div class="text-xs text-slate-400">
                最多 {{ row.maxAttempts }} 次
              </div>
            </td>
            <td class="font-mono">{{ row.number }}</td>
            <td class="font-mono">{{ row.owner }}</td>
            <td>{{ modeLabel[row.mode] || row.mode }}</td>
            <td>
              <span class="status">{{
                statusLabel[row.status] || row.status
              }}</span>
            </td>
            <td class="text-xs text-slate-500">{{ row.scheduledAt || "-" }}</td>
            <td>
              <div class="flex gap-1">
                <button
                  title="查看尝试记录"
                  class="icon-button"
                  @click="state.showAttempts(row)"
                >
                  <ListRestart class="h-4 w-4" /></button
                ><button
                  v-if="row.status === 'PENDING'"
                  title="暂停任务"
                  class="icon-button"
                  :disabled="state.controllingId.value === row.id"
                  @click="state.control(row, 'PAUSE')"
                >
                  <Pause class="h-4 w-4" /></button
                ><button
                  v-if="row.status === 'PAUSED'"
                  title="恢复任务"
                  class="icon-button"
                  :disabled="state.controllingId.value === row.id"
                  @click="state.control(row, 'RESUME')"
                >
                  <Play class="h-4 w-4" /></button
                ><button
                  v-if="['PENDING', 'PAUSED', 'RUNNING'].includes(row.status)"
                  title="取消任务"
                  class="icon-button text-rose-600"
                  :disabled="state.controllingId.value === row.id"
                  @click="state.control(row, 'CANCEL')"
                >
                  <X class="h-4 w-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer
      class="mt-4 flex items-center justify-end gap-3 text-sm text-slate-500"
    >
      <button
        class="secondary-button"
        :disabled="state.page.value === 1"
        @click="state.previous"
      >
        上一页</button
      ><span>第 {{ state.page.value }} 页</span
      ><button
        class="secondary-button"
        :disabled="!state.hasNext.value"
        @click="state.next"
      >
        下一页
      </button>
    </footer>

    <el-dialog
      v-model="state.createVisible.value"
      title="新建自动外呼任务"
      width="min(34rem, calc(100vw - 2rem))"
      append-to-body
      ><form
        class="grid grid-cols-1 gap-4 sm:grid-cols-2"
        @submit.prevent="state.create"
      >
        <label class="field sm:col-span-2"
          ><span>执行坐席</span
          ><select
            v-model="state.form.value.owner"
            :disabled="state.agentsLoading.value"
          >
            <option value="">
              {{ state.agentsLoading.value ? "坐席加载中..." : "请选择" }}
            </option>
            <option
              v-for="agent in state.agents.value"
              :key="agent.id"
              :value="agent.workNo"
            >
              {{ agent.workNo }} ·
              {{ agent.agentName || agent.realName || "未命名" }}
            </option>
          </select>
          <span v-if="state.agentsError.value" class="text-xs text-rose-600">
            {{ state.agentsError.value }}
            <button class="font-semibold underline" @click="state.loadAgents">
              重试
            </button>
          </span></label
        ><label class="field sm:col-span-2"
          ><span>被叫号码</span
          ><input v-model="state.form.value.number" /></label
        ><label class="field"
          ><span>外呼模式</span
          ><select v-model="state.form.value.mode">
            <option value="PROGRESSIVE">坐席先接</option>
            <option value="NOTIFICATION">通知外呼</option>
          </select></label
        ><label class="field"
          ><span>最多尝试</span
          ><select v-model="state.form.value.maxAttempts">
            <option :value="1">1 次</option>
            <option :value="2">2 次</option>
            <option :value="3">3 次</option>
          </select></label
        >
        <p class="sm:col-span-2 text-xs text-slate-500">
          任务创建后由服务端在配置的工作时段内调度；通知外呼依赖通知音频配置。
        </p>
        <div class="sm:col-span-2 flex justify-end gap-2">
          <button
            type="button"
            class="secondary-button"
            @click="state.createVisible.value = false"
          >
            取消</button
          ><button class="primary-button" :disabled="state.saving.value">
            {{ state.saving.value ? "创建中..." : "创建任务" }}
          </button>
        </div>
      </form></el-dialog
    >

    <el-dialog
      v-model="state.attemptsVisible.value"
      :title="`任务执行记录 · ${state.selectedJob.value?.id || ''}`"
      width="min(46rem, calc(100vw - 2rem))"
      append-to-body
      ><div v-if="state.attemptsLoading.value" class="empty">
        正在加载执行记录...
      </div>
      <div v-else-if="state.attempts.value.length === 0" class="empty">
        任务尚未产生呼叫尝试
      </div>
      <div v-else class="divide-y divide-slate-200 border-y border-slate-200">
        <article
          v-for="attempt in state.attempts.value"
          :key="attempt.id"
          class="grid grid-cols-2 gap-2 py-3 text-sm sm:grid-cols-4"
        >
          <div>
            <span class="meta">尝试</span
            ><strong>第 {{ attempt.attemptNo }} 次</strong>
          </div>
          <div>
            <span class="meta">状态</span
            >{{ statusLabel[attempt.status] || attempt.status }}
          </div>
          <div>
            <span class="meta">结果</span>{{ attempt.result || "等待结果" }}
          </div>
          <div>
            <span class="meta">通话 ID</span
            ><span class="font-mono text-xs break-all">{{
              attempt.callId || "-"
            }}</span>
          </div>
          <div class="col-span-2 sm:col-span-4 text-xs text-slate-500">
            {{ attempt.startedAt || "-" }} → {{ attempt.endedAt || "未结束"
            }}<span v-if="attempt.failureReason">
              · {{ attempt.failureReason }}</span
            >
          </div>
        </article>
      </div></el-dialog
    >
  </section>
</template>

<style scoped>
th,
td {
  padding: 0.75rem 1rem;
}
.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: #64748b;
}
.field {
  display: flex;
  min-width: 12rem;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #475569;
}
.field input,
.field select {
  min-height: 2.5rem;
  border: 1px solid #cbd5e1;
  background: #fff;
  padding: 0.55rem 0.75rem;
  font-size: 0.875rem;
  color: #0f172a;
}
.icon-button,
.primary-button,
.secondary-button {
  display: inline-flex;
  min-height: 2.25rem;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0 0.8rem;
  font-size: 0.8rem;
  font-weight: 600;
}
.icon-button {
  width: 2.25rem;
  border: 1px solid #cbd5e1;
  background: #fff;
  padding: 0;
}
.primary-button {
  background: #2563eb;
  color: #fff;
}
.secondary-button {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
}
.icon-button:disabled,
.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.status {
  display: inline-flex;
  background: #f1f5f9;
  padding: 0.2rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #334155;
}
.meta {
  display: block;
  font-size: 0.7rem;
  color: #94a3b8;
}
</style>
