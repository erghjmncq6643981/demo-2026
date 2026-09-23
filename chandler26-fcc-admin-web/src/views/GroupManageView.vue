<script setup lang="ts">
import { useGroupManagement } from '../features/groups/composables/useGroupManagement';
import GroupMemberTable from '../features/groups/components/GroupMemberTable.vue';
import OrgTreeItem from './OrgTreeItem.vue';
import { KeyRound, ShieldCheck, Copy, Check } from 'lucide-vue-next';

const {
  searchOrg,
  treeData,
  selectedNodeId,
  selectedDept,
  selectedDeptPath,
  selectedDeptCode,
  selectedDeptType,
  selectedDeptDesc,
  strategy,
  enableGroupPickup,
  exclusiveNumberPool,
  searchMemberQuery,
  triggerToast,
  currentMembers,
  loadingMembers,
  membersError,
  memberTotal,
  handleSelectNode,
  contextMenu,
  handleTreeContextMenu,
  showAddDeptModal,
  newDeptName,
  newDeptCode,
  newDeptType,
  newDeptStrategy,
  showEditDeptModal,
  editDeptName,
  editDeptCode,
  editDeptType,
  editDeptStrategy,
  showDeleteDeptModal,
  deletingDeptNode,
  openAddDept,
  openEditDept,
  openDeleteDept,
  handleConfirmAddDept,
  handleConfirmEditDept,
  handleConfirmDeleteDept,
  handleSaveGroupConfig,
  memberPage,
  memberPageSize,
  totalMemberPages,
  memberPageOptions,
  changeMemberPage,
  changeMemberPageSize,
  retryMembers,
  showAddAgentModal,
  newAgentName,
  newAgentWorkNo,
  newAgentPhone,
  newAgentRoleCode,
  newAgentMemberRole,
  newAgentPriority,
  newAgentPassword,
  openAddAgentModal,
  handleConfirmCreateAgent,
  showBindAgentModal,
  selectedBindAgentId,
  bindMemberRole,
  bindPriority,
  loadingSystemAgents,
  openBindAgentModal,
  availableAgentsToBind,
  handleConfirmBindAgent,
  showResetPasswordModal,
  resettingMember,
  resetPasswordInput,
  resetSubmitting,
  openResetPasswordModal,
  handleConfirmResetPassword,
  showCredentialModal,
  deliveredCredential,
  isCopied,
  copyCredential,
  editDeptId,
  showEditMemberModal,
  editingMember,
  editMemberName,
  editMemberPhone,
  editMemberRoleCode,
  editMemberRole,
  editMemberPriority,
  handleOpenEditMember,
  handleConfirmEditMember,
  handleUnbindMember,
  handleDeleteMemberAccount,
  strategyDesc
} = useGroupManagement();
</script>

<template>
  <div class="h-full flex-1 flex gap-5 overflow-hidden">
    
    <!-- 提示已统一收敛到全局反馈层 (src/utils/feedback.ts) -->

    <!-- ========================================================================= -->
    <!-- 1. 左侧企业组织架构树 (真实数据库驱动，单一顶级根节点初始，精简为 w-60) -->
    <!-- ========================================================================= -->
    <div class="w-60 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-3.5 flex flex-col shrink-0">
      
      <!-- 顶栏：标题与快捷新增 -->
      <div class="flex items-center justify-between pb-2.5 border-b border-slate-100">
        <div class="flex items-center gap-1.5">
          <span class="text-base">🏢</span>
          <h2 class="text-xs font-black text-slate-900 tracking-tight">组织与技能组</h2>
        </div>
        <button
          @click="openAddDept"
          class="px-2 py-1 bg-blue-50 hover:bg-blue-100 text-[#1677ff] rounded-lg text-[11px] font-bold transition flex items-center gap-1 cursor-pointer"
          title="在选中部门下创建子部门或技能组"
        >
          <span>➕</span>
          <span>新建</span>
        </button>
      </div>

      <!-- 搜索栏 -->
      <div class="relative my-2.5">
        <input
          v-model="searchOrg"
          type="text"
          placeholder="搜索部门或技能组..."
          class="w-full bg-[#f8fafc] border border-slate-200 rounded-xl pl-3 pr-7 py-1.5 text-xs text-slate-700 placeholder-slate-400 focus:outline-none focus:border-[#1677ff] transition"
        />
        <span class="absolute right-2 top-2 text-slate-400 text-xs">🔍</span>
      </div>

      <!-- 组织架构树主体 -->
      <div class="flex-1 overflow-y-auto space-y-1 pr-1 custom-scrollbar">
        <OrgTreeItem
          v-for="node in treeData"
          :key="node.id"
          :node="node"
          :selected-id="selectedNodeId"
          :search="searchOrg"
          @select="handleSelectNode"
          @contextmenu="handleTreeContextMenu"
        />
        <div v-if="treeData.length === 0" class="py-12 text-center text-xs text-slate-400">
          正在加载组织架构...
        </div>
      </div>

      <!-- 底部右键提示条 -->
      <div class="pt-2.5 mt-1.5 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-500">
        <div class="flex items-center gap-1">
          <span class="text-blue-500 font-bold">💡</span>
          <span>右键节点增删改</span>
        </div>
        <span class="font-mono text-slate-600 font-bold truncate max-w-[90px]" :title="selectedDept">
          {{ selectedDept }}
        </span>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 2. 右侧管理主区域 (卡片化、真实数据库驱动，支持上下垂直平滑滚动) -->
    <!-- ========================================================================= -->
    <div class="flex-1 min-h-0 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 flex flex-col overflow-y-auto space-y-6">
      
      <!-- 2.1 顶部部门信息与话务路由配置卡片 -->
      <div class="bg-gradient-to-r from-slate-50 via-white to-blue-50/30 rounded-2xl border border-slate-200/90 p-5 shadow-2xs space-y-4">
        <!-- 头部面包屑与技能组标题 -->
        <div class="flex items-center justify-between flex-wrap gap-3 pb-3 border-b border-slate-200/60">
          <div>
            <div class="flex items-center gap-2 text-xs text-slate-400 font-medium mb-1">
              <svg class="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>
              <span>{{ selectedDeptPath }}</span>
            </div>
            <div class="flex items-center gap-3">
              <h1 class="text-lg font-black text-slate-900 tracking-tight">{{ selectedDept }}</h1>
              <span class="px-2.5 py-0.5 rounded-full bg-blue-100/70 text-[#1677ff] font-mono font-bold text-xs">
                {{ selectedDeptCode }}
              </span>
              <span class="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 text-xs font-bold border border-emerald-200">
                ● 运行就绪
              </span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="openEditDept()"
              class="px-3.5 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold shadow-2xs transition cursor-pointer"
            >
              ✏️ 编辑部门
            </button>
            <button
              @click="handleSaveGroupConfig"
              class="px-4 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-500/20 flex items-center gap-1.5 transition cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/></svg>
              <span>保存策略</span>
            </button>
          </div>
        </div>

        <!-- 部门简述 -->
        <p class="text-xs text-slate-500 font-medium leading-relaxed">
          {{ selectedDeptDesc }}
        </p>

        <!-- 核心话务属性与规则网格 -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 pt-1">
          <!-- 卡片 1: 坐席分配策略 (移除冗余解释文案以避免挤压列表) -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-2">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-blue-500">⚡</span>
                <span>坐席分配策略</span>
              </div>
              <span class="text-[10px] text-slate-400">ACD 规则</span>
            </div>
            <select
              v-model="strategy"
              class="w-full bg-[#f8fafc] border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-900 font-bold focus:outline-none focus:border-[#1677ff] cursor-pointer"
            >
              <option value="ROUND_ROBIN">轮询均摊 (Round-Robin)</option>
              <option value="LONGEST_IDLE">最长空闲优先 (Longest Idle)</option>
              <option value="PRIORITY">坐席优先级 (Priority)</option>
            </select>
          </div>

          <!-- 卡片 2: 同组代答机制 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-2">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-purple-500">📞</span>
                <span>同组代答机制</span>
              </div>
              <span
                class="text-[10px] font-bold px-1.5 py-0.5 rounded"
                :class="enableGroupPickup ? 'bg-blue-50 text-[#1677ff]' : 'bg-slate-100 text-slate-400'"
              >
                {{ enableGroupPickup ? '已开启' : '已关闭' }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-0.5">
              <span class="text-xs font-semibold text-slate-800">
                {{ enableGroupPickup ? '支持跨工位快速代答' : '仅目标分机振铃' }}
              </span>
              <button
                type="button"
                @click="enableGroupPickup = !enableGroupPickup; triggerToast(enableGroupPickup ? '已开启同组代答' : '已关闭同组代答')"
                class="relative inline-flex h-6 w-12 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none"
                :class="enableGroupPickup ? 'bg-[#1677ff]' : 'bg-[#cbd5e1]'"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out mt-0.5 ml-0.5"
                  :class="enableGroupPickup ? 'translate-x-6' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>

          <!-- 卡片 3: 号码池模式 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-2">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-emerald-500">🌐</span>
                <span>外呼线路池</span>
              </div>
              <span
                class="text-[10px] font-bold px-1.5 py-0.5 rounded"
                :class="exclusiveNumberPool ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-400'"
              >
                {{ exclusiveNumberPool ? '独立号码池' : '共享号码池' }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-0.5">
              <span class="text-xs font-semibold text-slate-800">
                {{ exclusiveNumberPool ? '组内专属外呼中继' : '共享公共网关中继' }}
              </span>
              <button
                type="button"
                @click="exclusiveNumberPool = !exclusiveNumberPool; triggerToast(exclusiveNumberPool ? '已开启独立号码池' : '已恢复共享号码池')"
                class="relative inline-flex h-6 w-12 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none"
                :class="exclusiveNumberPool ? 'bg-[#1677ff]' : 'bg-[#cbd5e1]'"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out mt-0.5 ml-0.5"
                  :class="exclusiveNumberPool ? 'translate-x-6' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>

          <!-- 卡片 4: 节点统计概览 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-1.5">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-indigo-500">👥</span>
                <span>在册坐席人数</span>
              </div>
              <span class="text-[10px] text-slate-400">实时统计</span>
            </div>
            <div class="flex items-center justify-between pt-1">
              <div class="text-2xl font-black text-slate-900 font-mono">
                {{ currentMembers.length }} <span class="text-xs font-bold text-slate-400">人</span>
              </div>
              <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-bold">
                {{ selectedDeptType }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <GroupMemberTable
        v-model:search-query="searchMemberQuery"
        :rows="currentMembers"
        :loading="loadingMembers"
        :error="membersError"
        :total="memberTotal"
        :page="memberPage"
        :page-size="memberPageSize"
        :total-pages="totalMemberPages"
        :page-options="memberPageOptions"
        :selected-node-id="selectedNodeId"
        @add="openAddAgentModal"
        @bind="openBindAgentModal"
        @edit="handleOpenEditMember"
        @reset-password="openResetPasswordModal"
        @unbind="handleUnbindMember"
        @delete-account="handleDeleteMemberAccount"
        @retry="retryMembers"
        @change-page="changeMemberPage"
        @change-page-size="changeMemberPageSize"
      />
    </div>

    <!-- ==================== 右键悬浮上下文菜单 ==================== -->
    <div
      v-if="contextMenu.visible"
      :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
      class="fixed z-50 bg-white border border-slate-200 rounded-xl shadow-2xl py-1.5 w-48 text-xs select-none backdrop-blur-md"
      @click.stop
    >
      <div class="px-3 py-1.5 text-[11px] text-slate-400 border-b border-slate-100 font-bold truncate">
        📂 {{ contextMenu.node?.name }}
      </div>
      <button
        @click="openAddDept"
        class="w-full text-left px-3 py-2 text-slate-700 hover:bg-blue-50 hover:text-[#1677ff] flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>➕</span>
        <span class="font-medium">新增子部门/技能组</span>
      </button>
      <button
        @click="openEditDept()"
        class="w-full text-left px-3 py-2 text-slate-700 hover:bg-blue-50 hover:text-[#1677ff] flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>✏️</span>
        <span class="font-medium">编辑此部门</span>
      </button>
      <div class="h-px bg-slate-100 my-1"></div>
      <button
        @click="openDeleteDept"
        class="w-full text-left px-3 py-2 text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>🗑️</span>
        <span class="font-medium">删除该部门节点</span>
      </button>
    </div>

    <!-- ==================== 弹窗 1: 新增子部门 / 技能组 ==================== -->
    <div v-if="showAddDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">新增子部门 / 技能组</span>
          <button @click="showAddDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div class="text-slate-500">
            上级部门: <strong class="text-slate-800">{{ selectedDept }}</strong>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">部门/技能组名称 *</label>
            <input
              v-model="newDeptName"
              type="text"
              placeholder="例如：白班一组 / 售前咨询中心"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">唯一编码 (英文大写) *</label>
            <input
              v-model="newDeptCode"
              type="text"
              placeholder="例如：Q-HOTLINE-01"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono uppercase text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组织类型</label>
              <select v-model="newDeptType" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="SKILL">话务技能组</option>
                <option value="CENTER">业务管理中心</option>
                <option value="COMPANY">子公司</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">路由分发策略</label>
              <select v-model="newDeptStrategy" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="ROUND_ROBIN">轮询均摊</option>
                <option value="LONGEST_IDLE">最长空闲优先</option>
                <option value="PRIORITY">优先级分发</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmAddDept" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认新增
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 2: 编辑部门/技能组 ==================== -->
    <div v-if="showEditDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">编辑部门/技能组</span>
          <button @click="showEditDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>

        <!-- 当前组织节点信息卡片 -->
        <div class="bg-slate-50 border border-slate-200/80 rounded-xl p-3 text-xs space-y-1.5">
          <div class="flex items-center justify-between">
            <span class="text-slate-500 font-medium">当前选中节点</span>
            <span class="font-bold text-slate-800">{{ selectedDept }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-slate-500 font-medium">组织层级路径</span>
            <span class="font-mono text-slate-600 text-[11px] truncate max-w-[220px]" :title="selectedDeptPath || selectedDept">
              {{ selectedDeptPath || selectedDept }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-slate-500 font-medium">节点 ID</span>
            <span class="font-mono text-slate-500 text-[11px]">{{ editDeptId || selectedNodeId }}</span>
          </div>
        </div>

        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-bold">部门名称 *</label>
            <input
              v-model="editDeptName"
              type="text"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="block text-slate-700 font-bold">部门唯一编码</label>
              <span class="text-[11px] text-slate-400">不可修改 (系统全局唯一标识)</span>
            </div>
            <input
              :value="editDeptCode"
              disabled
              type="text"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono uppercase text-slate-500 bg-slate-100 cursor-not-allowed"
            />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组织类型</label>
              <select v-model="editDeptType" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="COMPANY">顶级集团/公司</option>
                <option value="CENTER">业务管理中心</option>
                <option value="SKILL">话务技能组</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">路由分发策略</label>
              <select v-model="editDeptStrategy" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="ROUND_ROBIN">轮询均摊</option>
                <option value="LONGEST_IDLE">最长空闲优先</option>
                <option value="PRIORITY">优先级分发</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button @click="showEditDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmEditDept" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            保存修改
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 3: 删除部门节点确认 ==================== -->
    <div v-if="showDeleteDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">确认删除部门</span>
          <button @click="showDeleteDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="text-xs text-slate-600 leading-relaxed">
          确定要删除部门【<strong class="text-slate-900">{{ deletingDeptNode?.name }}</strong>】吗？<br />
          <span class="text-rose-500 font-medium">注意：该部门及其下级关联的所有配置将从数据库移除。</span>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showDeleteDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmDeleteDept" class="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认删除
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 4: ➕ 新增坐席并加入组 ==================== -->
    <div v-if="showAddAgentModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">新增坐席并加入技能组</span>
            <div class="text-[11px] text-slate-400">将创建坐席人员档案，并直接绑定到【{{ selectedDept }}】</div>
          </div>
          <button @click="showAddAgentModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">坐席姓名 *</label>
              <input v-model="newAgentName" type="text" placeholder="如 舒欣" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">坐席工号 *</label>
              <input v-model="newAgentWorkNo" type="text" placeholder="如 90105" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">联系手机</label>
              <input v-model="newAgentPhone" type="text" placeholder="如 13800138000" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
              <select v-model="newAgentMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="MEMBER">👤 普通坐席</option>
                <option value="LEADER">👑 班长席 / 组长</option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">系统角色权限</label>
              <select v-model="newAgentRoleCode" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="AGENT">普通坐席</option>
                <option value="SUPERVISOR">主管席</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">调度优先级 (数值越小越先分发)</label>
              <input v-model.number="newAgentPriority" type="number" min="0" max="100" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">初始登录口令</label>
            <input
              v-model="newAgentPassword"
              type="password"
              placeholder="留空由系统自动生成强口令 (8-64位)"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
            <span class="text-[11px] text-slate-400 mt-1 block">可手动指定初始登录口令；留空则系统自动随机生成并在创建成功后展示交付凭据。</span>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddAgentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmCreateAgent" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认创建并入组
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 5: 🔗 绑定已有坐席 ==================== -->
    <div v-if="showBindAgentModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">绑定已有坐席至技能组</span>
            <div class="text-[11px] text-slate-400">从系统现有坐席库中选择人员，关联至【{{ selectedDept }}】</div>
          </div>
          <button @click="showBindAgentModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        
        <div v-if="loadingSystemAgents" class="py-8 text-center text-xs text-slate-400">
          正在读取系统坐席档案...
        </div>
        <div v-else class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-bold">选择坐席人员 *</label>
            <select
              v-model="selectedBindAgentId"
              class="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff] bg-white font-medium"
            >
              <option :value="null" disabled>-- 请选择待绑定的坐席 --</option>
              <option
                v-for="agent in availableAgentsToBind"
                :key="agent.id"
                :value="agent.id"
              >
                {{ agent.agentName || agent.realName }} (工号: {{ agent.workNo }}) - 手机: {{ agent.phoneNumber || agent.phone || '无' }}
              </option>
            </select>
            <div v-if="availableAgentsToBind.length === 0" class="text-[11px] text-amber-600 mt-1">
              提示：系统中暂无未入组的坐席，可通过「新增坐席」录入新员工。
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
              <select v-model="bindMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="MEMBER">👤 普通坐席</option>
                <option value="LEADER">👑 班长席 / 组长</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">调度优先级</label>
              <input v-model.number="bindPriority" type="number" min="0" max="100" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showBindAgentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button
            :disabled="!selectedBindAgentId"
            @click="handleConfirmBindAgent"
            class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 disabled:opacity-40 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs"
          >
            确认绑定入组
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 6: 修改坐席资料与组内配置 ==================== -->
    <div v-if="showEditMemberModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">修改坐席资料与组内配置</span>
            <div class="text-[11px] text-slate-400">所属技能组: {{ selectedDept }}</div>
          </div>
          <button @click="showEditMemberModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>

        <div class="space-y-3 text-xs">
          <!-- 坐席工号：不可修改 -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="block text-slate-700 font-bold">坐席工号</label>
              <span class="text-[11px] text-slate-400">不可修改 (系统唯一登录工号)</span>
            </div>
            <input
              :value="editingMember?.workNo"
              disabled
              type="text"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-500 bg-slate-100 cursor-not-allowed"
            />
          </div>

          <!-- 坐席姓名与联系手机 -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">坐席姓名 *</label>
              <input
                v-model="editMemberName"
                type="text"
                placeholder="坐席真实姓名"
                class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
              />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">联系手机</label>
              <input
                v-model="editMemberPhone"
                type="text"
                placeholder="手机号码"
                class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]"
              />
            </div>
          </div>

          <!-- 系统权限与组内身份 -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">系统角色权限</label>
              <select v-model="editMemberRoleCode" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="AGENT">普通坐席</option>
                <option value="SUPERVISOR">主管席</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
              <select v-model="editMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="MEMBER">👤 普通坐席</option>
                <option value="LEADER">👑 班长席 / 组长</option>
              </select>
            </div>
          </div>

          <!-- 调度优先级 -->
          <div>
            <label class="block text-slate-700 mb-1 font-bold">调度优先级 (数值越小优先级越高)</label>
            <input
              v-model.number="editMemberPriority"
              type="number"
              min="0"
              max="100"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>

          <!-- 密码指引说明 -->
          <div class="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80 text-[11px] text-slate-500 flex items-center justify-between">
            <span>💡 提示：如需重置或修改坐席登录密码，请点击操作栏的「重置口令」。</span>
          </div>
        </div>

        <div class="flex justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button @click="showEditMemberModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmEditMember" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            保存修改
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 7: 重置坐席登录口令 ==================== -->
    <div v-if="showResetPasswordModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <KeyRound class="w-4 h-4 text-indigo-600" />
            <span class="font-bold text-sm text-slate-900">重置坐席登录口令</span>
          </div>
          <button @click="showResetPasswordModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>

        <div class="bg-slate-50 border border-slate-200/80 rounded-xl p-3 text-xs space-y-1">
          <div class="flex justify-between">
            <span class="text-slate-500 font-medium">坐席姓名</span>
            <span class="font-bold text-slate-800">{{ resettingMember?.agentName }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-slate-500 font-medium">登录工号</span>
            <span class="font-mono font-bold text-slate-800">{{ resettingMember?.workNo }}</span>
          </div>
        </div>

        <div class="space-y-2 text-xs">
          <label class="block text-slate-700 font-bold">新登录口令</label>
          <input
            v-model="resetPasswordInput"
            type="text"
            placeholder="留空由系统自动生成随机强密码"
            class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]"
          />
          <p class="text-[11px] text-slate-400 leading-relaxed">
            支持 8-64 位自定义口令。若留空，系统将自动生成高强度随机密码并在完成后弹窗展示。
          </p>
        </div>

        <div class="flex justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button
            @click="showResetPasswordModal = false"
            class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer"
          >
            取消
          </button>
          <button
            @click="handleConfirmResetPassword"
            :disabled="resetSubmitting"
            class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs disabled:opacity-50"
          >
            {{ resetSubmitting ? '正在重置...' : '确认重置' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 8: 凭据交付卡片 ==================== -->
    <div v-if="showCredentialModal && deliveredCredential" class="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white border border-slate-100 rounded-2xl p-6 shadow-2xl max-w-md w-full space-y-4 animate-in fade-in zoom-in-95 duration-150">
        <div class="flex items-center space-x-3 pb-3 border-b border-slate-100">
          <div class="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-200 shrink-0">
            <ShieldCheck class="w-6 h-6" />
          </div>
          <div>
            <h3 class="text-sm font-black text-slate-900">坐席登录凭据交付</h3>
            <p class="text-[11px] text-slate-400">口令采用单向哈希加密存储，请及时复制并安全交付坐席</p>
          </div>
        </div>

        <div class="bg-slate-50 border border-slate-200/80 rounded-xl p-4 space-y-3 text-xs">
          <div class="flex justify-between items-center py-1 border-b border-slate-200/60">
            <span class="text-slate-500 font-medium">坐席工号 (登录账号)</span>
            <span class="font-mono font-black text-slate-800 text-sm">{{ deliveredCredential.account }}</span>
          </div>
          <div class="flex justify-between items-center py-1 border-b border-slate-200/60">
            <span class="text-slate-500 font-medium">坐席姓名</span>
            <span class="font-bold text-slate-800">{{ deliveredCredential.displayName }}</span>
          </div>
          <div class="flex justify-between items-center py-1">
            <span class="text-slate-500 font-medium">初始登录口令</span>
            <span class="font-mono font-black text-brand-600 bg-brand-50 border border-brand-200 px-2 py-0.5 rounded text-sm select-all">
              {{ deliveredCredential.initialPassword || '(未设/保持原样)' }}
            </span>
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-2">
          <button
            @click="copyCredential"
            class="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 transition cursor-pointer shadow-xs"
          >
            <Check v-if="isCopied" class="w-3.5 h-3.5 text-emerald-400" />
            <Copy v-else class="w-3.5 h-3.5" />
            <span>{{ isCopied ? '已复制到剪贴板' : '一键复制凭据' }}</span>
          </button>
          <button
            @click="showCredentialModal = false"
            class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold transition cursor-pointer"
          >
            完成并关闭
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 9999px;
}
</style>
