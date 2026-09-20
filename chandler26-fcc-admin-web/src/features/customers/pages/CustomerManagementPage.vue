<script setup lang="ts">
import {
  Pencil,
  Plus,
  RefreshCw,
  Search,
  UsersRound,
  X,
} from "lucide-vue-next";
import { useCustomerManagement } from "../composables/useCustomerManagement";

const state = useCustomerManagement();
</script>

<template>
  <section class="h-full min-w-0 overflow-y-auto">
    <header class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-slate-900">客户资料</h2>
        <p class="mt-1 text-sm text-slate-500">统一管理客户归属和联系资料。</p>
      </div>
      <div class="flex gap-2">
        <button
          title="刷新客户资料"
          class="icon-button"
          :disabled="state.loading.value"
          @click="state.load"
        >
          <RefreshCw
            class="h-4 w-4"
            :class="state.loading.value ? 'animate-spin' : ''"
          />
        </button>
        <button class="primary-button" @click="state.create">
          <Plus class="h-4 w-4" />新增客户
        </button>
      </div>
    </header>

    <div
      class="mb-4 flex flex-wrap items-end gap-3 border-y border-slate-200 bg-white px-4 py-3"
    >
      <label class="field"
        ><span>负责坐席</span
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
      <label class="field"
        ><span>客户号码</span
        ><input
          v-model="state.phoneFilter.value"
          placeholder="输入完整号码"
          @keyup.enter="state.search"
      /></label>
      <button class="secondary-button" @click="state.search">
        <Search class="h-4 w-4" />查询
      </button>
      <button class="text-button" @click="state.reset">重置</button>
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
      <table class="w-full min-w-[780px] text-left text-sm">
        <thead class="bg-slate-50 text-xs text-slate-500">
          <tr>
            <th>客户</th>
            <th>联系电话</th>
            <th>单位</th>
            <th>负责坐席</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="state.loading.value">
            <td colspan="5" class="empty">正在加载客户资料...</td>
          </tr>
          <tr v-else-if="state.rows.value.length === 0">
            <td colspan="5" class="empty">
              <UsersRound
                class="mx-auto mb-2 h-6 w-6 text-slate-300"
              />暂无客户资料
            </td>
          </tr>
          <tr v-for="row in state.rows.value" v-else :key="row.id">
            <td>
              <div class="font-semibold text-slate-900">{{ row.name }}</div>
              <div class="id-text" :title="row.id">{{ row.id }}</div>
            </td>
            <td class="font-mono">{{ row.phoneNumber }}</td>
            <td>{{ row.companyName || "-" }}</td>
            <td class="font-mono">{{ row.owner }}</td>
            <td>
              <button
                title="编辑客户"
                class="icon-button"
                @click="state.edit(row)"
              >
                <Pencil class="h-4 w-4" />
              </button>
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
      v-model="state.dialogVisible.value"
      :title="state.editing.value ? '编辑客户资料' : '新增客户资料'"
      width="min(36rem, calc(100vw - 2rem))"
      append-to-body
    >
      <div v-if="state.detailLoading.value" class="empty">正在加载详情...</div>
      <form
        v-else
        class="grid grid-cols-1 gap-4 sm:grid-cols-2"
        @submit.prevent="state.save"
      >
        <label class="field"
          ><span>客户姓名</span
          ><input v-model="state.form.value.name" maxlength="128"
        /></label>
        <label class="field"
          ><span>联系电话</span><input v-model="state.form.value.phoneNumber"
        /></label>
        <label class="field sm:col-span-2"
          ><span>单位</span
          ><input v-model="state.form.value.companyName" maxlength="128"
        /></label>
        <label class="field sm:col-span-2"
          ><span>负责坐席</span
          ><select
            v-model="state.form.value.owner"
            :disabled="state.editing.value || state.agentsLoading.value"
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
          </select></label
        >
        <label class="field sm:col-span-2"
          ><span>备注</span
          ><textarea v-model="state.form.value.notes" rows="4"></textarea>
        </label>
        <div class="sm:col-span-2 flex justify-end gap-2">
          <button
            type="button"
            class="secondary-button"
            @click="state.dialogVisible.value = false"
          >
            <X class="h-4 w-4" />取消</button
          ><button class="primary-button" :disabled="state.saving.value">
            {{ state.saving.value ? "保存中..." : "保存" }}
          </button>
        </div>
      </form>
    </el-dialog>
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
.id-text {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: #94a3b8;
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
.field select,
.field textarea {
  min-height: 2.5rem;
  border: 1px solid #cbd5e1;
  background: #fff;
  padding: 0.55rem 0.75rem;
  font-size: 0.875rem;
  color: #0f172a;
}
.field textarea {
  resize: vertical;
}
.icon-button,
.primary-button,
.secondary-button,
.text-button {
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
.text-button {
  color: #475569;
}
.icon-button:disabled,
.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
