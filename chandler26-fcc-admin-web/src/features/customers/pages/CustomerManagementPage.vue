<script setup lang="ts">
import {
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Users,
  UsersRound,
  X,
} from "lucide-vue-next";
import { useCustomerManagement } from "../composables/useCustomerManagement";

const state = useCustomerManagement();
</script>

<template>
  <div class="space-y-6">
    <!-- Header Card -->
    <div
      class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card"
    >
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <Users class="w-5 h-5 text-brand-600" />
          客户资料管理
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          统一管理客户归属、联系方式及坐席分配
        </p>
      </div>

      <div class="flex items-center space-x-3">
        <button
          title="刷新客户资料"
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
          @click="state.create"
        >
          <Plus class="w-4 h-4" />
          <span>新增客户</span>
        </button>
      </div>
    </div>

    <!-- Filter Card -->
    <div
      class="flex flex-wrap items-end gap-3 text-sm bg-white p-4 rounded-3xl border border-slate-100 shadow-card"
    >
      <div class="w-48">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">负责坐席</label>
        <select
          v-model="state.ownerFilter.value"
          :disabled="state.agentsLoading.value"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
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
        </select>
      </div>

      <div class="w-48">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">客户号码</label>
        <input
          v-model="state.phoneFilter.value"
          placeholder="输入完整号码"
          @keyup.enter="state.search"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium placeholder:text-slate-400"
        />
      </div>

      <button
        class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        @click="state.search"
      >
        <Search class="w-3.5 h-3.5" />
        <span>查询</span>
      </button>

      <button
        class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer"
        @click="state.reset"
      >
        重置
      </button>

      <span v-if="state.agentsError.value" class="text-xs text-rose-600 flex items-center gap-1 ml-2">
        {{ state.agentsError.value }}
        <button class="font-bold underline cursor-pointer" @click="state.loadAgents">
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

    <!-- Customer Table Card -->
    <div
      class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6"
    >
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr
              class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold"
            >
              <th class="py-3.5 px-4">客户姓名 / ID</th>
              <th class="py-3.5 px-4">联系电话</th>
              <th class="py-3.5 px-4">单位名称</th>
              <th class="py-3.5 px-4">负责坐席</th>
              <th class="py-3.5 px-4 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="state.loading.value">
              <td colspan="5" class="py-12 text-center text-slate-400">
                <RefreshCw
                  class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500"
                />
                正在加载客户资料...
              </td>
            </tr>
            <tr v-else-if="state.rows.value.length === 0">
              <td colspan="5" class="py-12 text-center text-slate-400 font-medium">
                <UsersRound class="w-8 h-8 mx-auto mb-2 text-slate-300" />
                暂无客户资料
              </td>
            </tr>
            <tr
              v-for="row in state.rows.value"
              v-else
              :key="row.id"
              class="hover:bg-slate-50/80 transition-colors"
            >
              <td class="py-4 px-4">
                <div class="font-bold text-slate-900">{{ row.name }}</div>
                <div
                  class="font-mono text-xs text-slate-400 truncate max-w-[12rem]"
                  :title="row.id"
                >
                  {{ row.id }}
                </div>
              </td>
              <td class="py-4 px-4 font-mono text-xs font-bold text-slate-800">
                {{ row.phoneNumber }}
              </td>
              <td class="py-4 px-4 text-xs text-slate-600">
                {{ row.companyName || "-" }}
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2.5 py-0.5 rounded-lg font-mono text-xs bg-slate-100 text-slate-800 border border-slate-200"
                >
                  {{ row.owner }}
                </span>
              </td>
              <td class="py-4 px-4 text-right">
                <button
                  title="编辑客户"
                  class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 border border-brand-200 rounded-xl text-xs font-bold inline-flex items-center gap-1 transition cursor-pointer"
                  @click="state.edit(row)"
                >
                  <Pencil class="w-3.5 h-3.5" />
                  <span>编辑</span>
                </button>
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

    <!-- Edit/Create Dialog -->
    <el-dialog
      v-model="state.dialogVisible.value"
      :title="state.editing.value ? '编辑客户资料' : '新增客户资料'"
      width="min(36rem, calc(100vw - 2rem))"
      append-to-body
      class="rounded-3xl"
    >
      <div v-if="state.detailLoading.value" class="py-12 text-center text-slate-400">
        <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
        正在加载客户详情...
      </div>
      <form
        v-else
        class="grid grid-cols-1 gap-4 sm:grid-cols-2 p-1"
        @submit.prevent="state.save"
      >
        <div>
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >客户姓名 <span class="text-rose-500">*</span></label
          >
          <input
            v-model="state.form.value.name"
            maxlength="128"
            placeholder="请输入姓名"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium"
          />
        </div>

        <div>
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >联系电话 <span class="text-rose-500">*</span></label
          >
          <input
            v-model="state.form.value.phoneNumber"
            placeholder="请输入手机或座机号码"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium font-mono"
          />
        </div>

        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >单位名称</label
          >
          <input
            v-model="state.form.value.companyName"
            maxlength="128"
            placeholder="选填"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium"
          />
        </div>

        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >负责坐席 <span class="text-rose-500">*</span></label
          >
          <select
            v-model="state.form.value.owner"
            :disabled="state.editing.value || state.agentsLoading.value"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer disabled:bg-slate-100 disabled:cursor-not-allowed"
          >
            <option value="">
              {{ state.agentsLoading.value ? "坐席加载中..." : "请选择负责坐席" }}
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
        </div>

        <div class="sm:col-span-2">
          <label class="block text-xs font-bold text-slate-600 mb-1.5"
            >备注</label
          >
          <textarea
            v-model="state.form.value.notes"
            rows="3"
            placeholder="选填，客户偏好或服务背景说明"
            class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium resize-y"
          ></textarea>
        </div>

        <div class="sm:col-span-2 flex justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            type="button"
            class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer"
            @click="state.dialogVisible.value = false"
          >
            <X class="w-3.5 h-3.5 inline mr-1" />取消
          </button>
          <button
            type="submit"
            class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition cursor-pointer disabled:opacity-50"
            :disabled="state.saving.value"
          >
            {{ state.saving.value ? "保存中..." : "保存" }}
          </button>
        </div>
      </form>
    </el-dialog>
  </div>
</template>
