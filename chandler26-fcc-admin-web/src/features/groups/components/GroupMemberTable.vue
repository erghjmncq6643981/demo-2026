<script setup lang="ts">
import type { AgentGroupMemberVO } from '../../../api/agentApi';

const props = defineProps<{
  rows: AgentGroupMemberVO[];
  loading: boolean;
  error: string;
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  pageOptions: number[];
  selectedNodeId: string;
}>();

const searchQuery = defineModel<string>('searchQuery', { required: true });

const emit = defineEmits<{
  add: [];
  bind: [];
  edit: [member: AgentGroupMemberVO];
  resetPassword: [member: AgentGroupMemberVO];
  unbind: [member: AgentGroupMemberVO];
  deleteAccount: [member: AgentGroupMemberVO];
  retry: [];
  changePage: [page: number];
  changePageSize: [pageSize: number];
}>();

/**
 * 将每页条数选择框的原始值转换为数值并通知父级重新查询。
 *
 * @param event 原生选择框变更事件
 */
function handlePageSizeChange(event: Event) {
  const target = event.target as HTMLSelectElement;
  emit('changePageSize', Number(target.value));
}
</script>

<template>
  <div class="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div class="flex items-center gap-3">
        <div class="w-2.5 h-5 bg-indigo-500 rounded-full"></div>
        <h3 class="text-sm font-black text-slate-900">当前节点及子节点坐席</h3>
        <span class="px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-mono text-xs font-bold">
          共 {{ total }} 名
        </span>
      </div>

      <div class="flex items-center gap-2.5 text-xs">
        <div class="relative">
          <input
            v-model="searchQuery"
            type="search"
            placeholder="搜索姓名/工号/手机..."
            class="w-48 bg-[#f8fafc] border border-slate-200 rounded-xl pl-3 pr-7 py-1.5 text-xs text-slate-700 placeholder-slate-400 focus:outline-none focus:border-[#1677ff]"
          />
          <span class="absolute right-2 top-2 text-slate-400 text-xs">🔍</span>
        </div>
        <button
          type="button"
          class="px-3.5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-xs transition flex items-center gap-1 cursor-pointer"
          @click="emit('add')"
        >
          <span>➕</span>
          <span>新增坐席</span>
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-bold shadow-xs transition flex items-center gap-1 cursor-pointer"
          @click="emit('bind')"
        >
          <span>🔗</span>
          <span>绑定坐席</span>
        </button>
      </div>
    </div>

    <div class="border border-slate-200/90 rounded-xl overflow-x-auto">
      <table class="w-full min-w-[980px] text-xs text-center">
        <thead class="bg-[#f8fafc] text-slate-600 border-b border-slate-200 font-bold">
          <tr>
            <th class="py-3 px-4 text-left whitespace-nowrap">坐席姓名</th>
            <th class="py-3 px-4 whitespace-nowrap">工号</th>
            <th class="py-3 px-4 whitespace-nowrap">实际所属节点</th>
            <th class="py-3 px-4 whitespace-nowrap">联系电话</th>
            <th class="py-3 px-4 whitespace-nowrap">组内身份</th>
            <th class="py-3 px-4 whitespace-nowrap">调度优先级</th>
            <th class="py-3 px-4 whitespace-nowrap">系统角色</th>
            <th class="py-3 px-4 whitespace-nowrap">入组时间</th>
            <th class="py-3 px-4 text-right whitespace-nowrap">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 bg-white">
          <tr v-if="loading">
            <td colspan="9" class="py-12 text-center text-slate-400">正在加载组织子树成员...</td>
          </tr>
          <tr v-else-if="error">
            <td colspan="9" class="py-10 text-center">
              <p class="text-rose-600 mb-3">{{ error }}</p>
              <button
                type="button"
                class="px-3 py-1.5 rounded-lg bg-slate-900 text-white font-bold"
                @click="emit('retry')"
              >
                重新加载
              </button>
            </td>
          </tr>
          <template v-else>
            <tr
              v-for="member in rows"
              :key="member.agentId"
              class="hover:bg-blue-50/30 transition-colors"
            >
              <td class="py-3 px-4 text-left whitespace-nowrap">
                <div class="flex items-center gap-2.5">
                  <div class="w-7 h-7 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-500 text-white font-bold flex items-center justify-center text-xs shadow-2xs shrink-0">
                    {{ member.agentName ? member.agentName.slice(0, 1) : '坐' }}
                  </div>
                  <div class="min-w-0">
                    <div class="font-bold text-slate-900">{{ member.agentName }}</div>
                    <div class="max-w-40 truncate text-[10px] text-slate-400 font-mono" :title="member.agentId">
                      ID: {{ member.agentId }}
                    </div>
                  </div>
                </div>
              </td>
              <td class="py-3 px-4 font-mono font-bold text-slate-700 whitespace-nowrap">
                <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700">{{ member.workNo }}</span>
              </td>
              <td class="py-3 px-4 whitespace-nowrap">
                <div class="font-medium text-slate-700">{{ member.groupName }}</div>
                <div class="text-[10px]" :class="member.groupId === selectedNodeId ? 'text-indigo-600' : 'text-slate-400'">
                  {{ member.groupId === selectedNodeId ? '当前节点直属' : '来自子节点' }}
                </div>
              </td>
              <td class="py-3 px-4 font-mono text-slate-700 whitespace-nowrap">{{ member.phoneNumber || '-' }}</td>
              <td class="py-3 px-4 whitespace-nowrap">
                <span
                  class="px-2.5 py-0.5 rounded-full text-[11px] font-bold"
                  :class="member.memberRole === 'LEADER' ? 'bg-purple-100 text-purple-700 border border-purple-200' : 'bg-blue-50 text-blue-700'"
                >
                  {{ member.memberRole === 'LEADER' ? '👑 班长席 / 组长' : '👤 普通坐席' }}
                </span>
              </td>
              <td class="py-3 px-4 font-mono font-bold text-slate-700 whitespace-nowrap">
                <span class="px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200 text-[11px]">
                  优先级 {{ member.priority ?? 0 }}
                </span>
              </td>
              <td class="py-3 px-4 whitespace-nowrap text-slate-600 font-medium">
                {{ member.roleCode === 'SUPERVISOR' ? '主管' : '坐席' }}
              </td>
              <td class="py-3 px-4 font-mono text-slate-400 text-[11px] whitespace-nowrap">
                {{ member.createdAt ? member.createdAt.replace('T', ' ').slice(0, 19) : '-' }}
              </td>
              <td class="py-3 px-4 text-right whitespace-nowrap">
                <div class="inline-flex items-center gap-1.5">
                  <button type="button" class="text-[#1677ff] hover:bg-blue-50 px-2 py-1 rounded font-bold" @click="emit('edit', member)">修改</button>
                  <button type="button" class="text-indigo-600 hover:bg-indigo-50 px-2 py-1 rounded font-bold" @click="emit('resetPassword', member)">重置口令</button>
                  <button type="button" class="text-amber-600 hover:bg-amber-50 px-2 py-1 rounded font-bold" @click="emit('unbind', member)">解绑</button>
                  <button type="button" class="text-rose-600 hover:bg-rose-50 px-2 py-1 rounded font-bold" @click="emit('deleteAccount', member)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="9" class="py-12 text-center text-slate-400">
                <div class="text-2xl mb-1">📭</div>
                <div>当前节点及其子节点暂无匹配坐席</div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div class="flex items-center justify-between text-xs text-slate-500 pt-2">
      <span>共 {{ total }} 名坐席，第 {{ page }} / {{ totalPages }} 页</span>
      <div class="flex items-center gap-3">
        <div class="flex items-center gap-1">
          <button
            type="button"
            :disabled="page <= 1 || loading"
            class="w-6 h-6 border border-slate-200 rounded disabled:opacity-30"
            @click="emit('changePage', page - 1)"
          >&lt;</button>
          <button
            v-for="pageOption in pageOptions"
            :key="pageOption"
            type="button"
            class="w-6 h-6 rounded text-xs font-bold"
            :class="page === pageOption ? 'bg-[#1677ff] text-white' : 'border border-slate-200 hover:bg-slate-50 text-slate-700'"
            @click="emit('changePage', pageOption)"
          >
            {{ pageOption }}
          </button>
          <button
            type="button"
            :disabled="page >= totalPages || loading"
            class="w-6 h-6 border border-slate-200 rounded disabled:opacity-30"
            @click="emit('changePage', page + 1)"
          >&gt;</button>
        </div>
        <select
          :value="pageSize"
          class="border border-slate-200 rounded px-2 py-0.5 text-xs text-slate-600 bg-white"
          @change="handlePageSizeChange"
        >
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
      </div>
    </div>
  </div>
</template>
