<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { agentApi, type AgentVO, type AccountCredentialVO } from '../api/agentApi';
import {
  Users,
  UserPlus,
  RefreshCw,
  CheckCircle,
  AlertCircle,
  Award,
  PhoneCall,
  KeyRound,
  Copy,
  Check,
  Pencil,
  Search,
  RotateCcw,
  LogOut,
  Trash2,
} from 'lucide-vue-next';
import { confirmAction, toastSuccess, toastError } from '../utils/feedback';

const agents = ref<AgentVO[]>([]);
const availableSipExtensions = ref<string[]>([]);
const loading = ref(false);
const total = ref(0);
const successNotice = ref('');

// Pagination & Filters
const pageNum = ref(1);
const pageSize = ref(10);
const searchAgentName = ref('');
const searchPhoneNumber = ref('');
const searchLoginStatus = ref('');
const dateRange = ref<[string, string] | null>(null);

const callStatusConfig: Record<string, { label: string; class: string }> = {
  READY: { label: '空闲', class: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  BUSY: { label: '示忙', class: 'bg-amber-50 text-amber-700 border-amber-200' },
  RINGING: { label: '振铃中', class: 'bg-purple-50 text-purple-700 border-purple-200 animate-pulse' },
  CALLING: { label: '呼叫中', class: 'bg-blue-50 text-blue-700 border-blue-200' },
  TALKING: { label: '通话中', class: 'bg-rose-50 text-rose-700 border-rose-200' },
  ACW: { label: '话后整理', class: 'bg-orange-50 text-orange-700 border-orange-200' },
  REST: { label: '小休', class: 'bg-slate-100 text-slate-600 border-slate-200' },
};

// Modals
const isCreateModalOpen = ref(false);
const isEditModalOpen = ref(false);
const isBindingModalOpen = ref(false);
const isResetModalOpen = ref(false);
const isCredentialModalOpen = ref(false);
const submitting = ref(false);
const formError = ref('');

// New Agent Form
const newAgent = ref({
  workNo: '',
  agentName: '',
  roleCode: 'AGENT',
  phoneNumber: '',
  password: '',
});

// Edit Agent Form
const editingAgent = ref<AgentVO | null>(null);
const editForm = ref({
  agentName: '',
  phoneNumber: '',
  roleCode: 'AGENT',
  status: 'ENABLED',
});

// Credential Delivery Modal Data
const deliveredCredential = ref<AccountCredentialVO | null>(null);
const isCopied = ref(false);

// Reset Password Modal Data
const currentAgentForReset = ref<AgentVO | null>(null);
const resetPasswordInput = ref('');
const resetSubmitting = ref(false);
const resetError = ref('');

// Binding Form
const bindingForm = ref<{ workNo: string; endpointType: 'WEBRTC' | 'SIP'; endpointValue?: string }>({
  workNo: '',
  endpointType: 'WEBRTC',
  endpointValue: '',
});
const currentAgentForBinding = ref<AgentVO | null>(null);

const loadData = async () => {
  loading.value = true;
  try {
    const agentsRes = await agentApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      agentName: searchAgentName.value.trim() || undefined,
      phoneNumber: searchPhoneNumber.value.trim() || undefined,
      loginStatus: searchLoginStatus.value || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined,
    });
    agents.value = agentsRes.list || [];
    total.value = agentsRes.total || 0;
  } catch (err: any) {
    console.error('Failed to load agents:', err);
    toastError(err.message || '加载坐席人员失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNum.value = 1;
  void loadData();
};

const handleReset = () => {
  searchAgentName.value = '';
  searchPhoneNumber.value = '';
  searchLoginStatus.value = '';
  dateRange.value = null;
  pageNum.value = 1;
  void loadData();
};

const handlePageChange = (newPage: number) => {
  pageNum.value = newPage;
  void loadData();
};

const handleSizeChange = (newSize: number) => {
  pageSize.value = newSize;
  pageNum.value = 1;
  void loadData();
};

const handleForceLogout = async (ag: AgentVO) => {
  const name = ag.agentName || ag.realName || ag.workNo;
  const accepted = await confirmAction(
    `确定要强制将坐席「${name}」（工号：${ag.workNo}）下线吗？下线后其工作台将被强制退出。`,
    {
      title: '强制坐席下线',
      confirmText: '强制下线',
      danger: true,
    }
  );
  if (!accepted) return;
  try {
    await agentApi.logout(ag.id);
    toastSuccess(`坐席「${name}」已成功强制下线`);
    await loadData();
  } catch (err: any) {
    console.error('Failed to logout agent:', err);
    toastError(err.message || '强制下线失败');
  }
};

const handleDeleteAgent = async (ag: AgentVO) => {
  const name = ag.agentName || ag.realName || ag.workNo;
  const accepted = await confirmAction(
    `确认彻底注销并删除坐席【${name}】（工号：${ag.workNo}）的档案吗？此操作不可逆！`,
    {
      title: '删除坐席档案',
      confirmText: '确认删除',
      danger: true,
    }
  );
  if (!accepted) return;
  try {
    await agentApi.delete(ag.id);
    toastSuccess(`坐席【${name}】档案已成功删除`);
    await loadData();
  } catch (err: any) {
    console.error('Failed to delete agent:', err);
    toastError(err.message || '删除坐席失败');
  }
};

const openCreateModal = () => {
  newAgent.value = {
    workNo: '',
    agentName: '',
    roleCode: 'AGENT',
    phoneNumber: '',
    password: '',
  };
  formError.value = '';
  isCreateModalOpen.value = true;
};

const handleCreateAgent = async () => {
  if (!newAgent.value.workNo || !newAgent.value.agentName) {
    formError.value = '请完整填写工号与姓名';
    return;
  }
  const pwd = newAgent.value.password.trim();
  if (pwd && (pwd.length < 8 || pwd.length > 64)) {
    formError.value = '登录密码长度需在 8 到 64 位之间';
    return;
  }
  submitting.value = true;
  formError.value = '';
  try {
    const cred = await agentApi.create({
      workNo: newAgent.value.workNo.trim(),
      agentName: newAgent.value.agentName.trim(),
      phoneNumber: newAgent.value.phoneNumber.trim() || undefined,
      roleCode: newAgent.value.roleCode,
      password: pwd || undefined,
    });
    isCreateModalOpen.value = false;
    deliveredCredential.value = {
      ...cred,
      account: cred?.account || newAgent.value.workNo.trim(),
      displayName: cred?.displayName || newAgent.value.agentName.trim(),
      initialPassword: cred?.initialPassword || (pwd ? pwd : '(已按指定密码设置)'),
    };
    isCopied.value = false;
    isCredentialModalOpen.value = true;
    await loadData();
  } catch (err: any) {
    formError.value = err.message || '录入失败，请检查工号或联系管理员';
  } finally {
    submitting.value = false;
  }
};

const openEditModal = (agent: AgentVO) => {
  editingAgent.value = agent;
  editForm.value = {
    agentName: agent.agentName || agent.realName || '',
    phoneNumber: agent.phoneNumber || agent.phone || '',
    roleCode: agent.roleCode || (agent.isSupervisor ? 'SUPERVISOR' : 'AGENT'),
    status: agent.status || 'ENABLED',
  };
  formError.value = '';
  isEditModalOpen.value = true;
};

const handleConfirmEditAgent = async () => {
  if (!editingAgent.value) return;
  if (!editForm.value.agentName.trim()) {
    formError.value = '坐席真实姓名不能为空';
    return;
  }
  submitting.value = true;
  formError.value = '';
  try {
    await agentApi.update({
      id: editingAgent.value.id,
      agentName: editForm.value.agentName.trim(),
      phoneNumber: editForm.value.phoneNumber.trim() || undefined,
      roleCode: editForm.value.roleCode,
      status: editForm.value.status,
    });
    isEditModalOpen.value = false;
    successNotice.value = `坐席【${editForm.value.agentName}】资料修改成功！`;
    await loadData();
    setTimeout(() => (successNotice.value = ''), 4000);
  } catch (err: any) {
    formError.value = err.message || '更新坐席资料失败';
  } finally {
    submitting.value = false;
  }
};

const openResetPasswordModal = (agent: AgentVO) => {
  currentAgentForReset.value = agent;
  resetPasswordInput.value = '';
  resetError.value = '';
  isResetModalOpen.value = true;
};

const handleConfirmResetPassword = async () => {
  if (!currentAgentForReset.value) return;
  const pwd = resetPasswordInput.value.trim();
  if (pwd && (pwd.length < 8 || pwd.length > 64)) {
    resetError.value = '密码长度需在 8 到 64 位之间';
    return;
  }
  resetSubmitting.value = true;
  resetError.value = '';
  try {
    const cred = await agentApi.resetPassword(currentAgentForReset.value.id, pwd || undefined);
    isResetModalOpen.value = false;
    deliveredCredential.value = {
      ...cred,
      account: cred?.account || currentAgentForReset.value.workNo,
      displayName: cred?.displayName || currentAgentForReset.value.agentName || currentAgentForReset.value.realName || '',
      initialPassword: cred?.initialPassword || (pwd ? pwd : '(已按指定密码设置)'),
    };
    isCopied.value = false;
    isCredentialModalOpen.value = true;
  } catch (err: any) {
    resetError.value = err.message || '重置密码失败';
  } finally {
    resetSubmitting.value = false;
  }
};

const copyCredential = async () => {
  if (!deliveredCredential.value) return;
  const text = `坐席工号：${deliveredCredential.value.account}\n坐席姓名：${deliveredCredential.value.displayName}\n登录密码：${deliveredCredential.value.initialPassword || ''}`;
  try {
    await navigator.clipboard.writeText(text);
    isCopied.value = true;
    setTimeout(() => (isCopied.value = false), 3000);
  } catch {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    isCopied.value = true;
    setTimeout(() => (isCopied.value = false), 3000);
  }
};

const openBindingModal = async (agent: AgentVO) => {
  currentAgentForBinding.value = agent;
  const endpoints = await agentApi.endpoints(agent.workNo);
  availableSipExtensions.value = endpoints.availableSipExtensions || [];
  bindingForm.value = {
    workNo: agent.workNo,
    endpointType: endpoints.activeEndpointType === 'SIP' ? 'SIP' : 'WEBRTC',
    endpointValue: endpoints.activeEndpointType === 'SIP' ? endpoints.activeEndpointValue : undefined,
  };
  formError.value = '';
  isBindingModalOpen.value = true;
};

const handleBindEndpoint = async () => {
  submitting.value = true;
  formError.value = '';
  try {
    await agentApi.switchEndpoint({
      workNo: bindingForm.value.workNo,
      endpointType: bindingForm.value.endpointType,
      endpointValue: bindingForm.value.endpointValue,
    });
    successNotice.value = `坐席 ${currentAgentForBinding.value?.agentName || currentAgentForBinding.value?.realName} 的当前接听终端已切换为 ${bindingForm.value.endpointType}。`;
    isBindingModalOpen.value = false;
    await loadData();
    setTimeout(() => (successNotice.value = ''), 5000);
  } catch (err: any) {
    formError.value = err.message || '绑定失败';
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="space-y-6">
    <!-- Notice -->
    <div v-if="successNotice" class="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center space-x-3 text-sm text-emerald-700 font-bold">
      <CheckCircle class="w-5 h-5 flex-shrink-0" />
      <span>{{ successNotice }}</span>
    </div>

    <!-- Header Actions -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-3xl border border-slate-100 shadow-card">
      <div>
        <h2 class="text-base font-black text-slate-900 flex items-center gap-2">
          <Users class="w-5 h-5 text-brand-600" />
          坐席管理
        </h2>
      </div>

      <div class="flex items-center space-x-3">
        <button
          @click="loadData"
          :disabled="loading"
          class="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl transition border border-slate-200 cursor-pointer"
          title="刷新列表"
        >
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>

        <button
          @click="openCreateModal"
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
        >
          <UserPlus class="w-4 h-4" />
          <span>录入坐席</span>
        </button>
      </div>
    </div>

    <!-- Filter Card -->
    <div
      class="flex flex-wrap items-end gap-3 text-sm bg-white p-4 rounded-3xl border border-slate-100 shadow-card"
    >
      <!-- 姓名 -->
      <div class="w-40">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">姓名</label>
        <input
          v-model="searchAgentName"
          placeholder="输入姓名"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium"
          @keyup.enter="handleSearch"
        />
      </div>

      <!-- 手机号码 -->
      <div class="w-44">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">手机号码</label>
        <input
          v-model="searchPhoneNumber"
          placeholder="输入手机号"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium font-mono"
          @keyup.enter="handleSearch"
        />
      </div>

      <!-- 登录状态 -->
      <div class="w-36">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">登录状态</label>
        <select
          v-model="searchLoginStatus"
          class="w-full bg-slate-50/70 border border-slate-200 rounded-xl px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500/20 font-medium cursor-pointer"
        >
          <option value="">全部状态</option>
          <option value="ONLINE">在线</option>
          <option value="OFFLINE">离线</option>
        </select>
      </div>

      <!-- 时间选择框（开始时间-结束时间） -->
      <div class="w-[370px]">
        <label class="block text-xs font-bold text-slate-600 mb-1.5">时间范围</label>
        <FccDateRangePicker
          v-model="dateRange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          @change="handleSearch"
        />
      </div>

      <div class="flex items-center gap-2">
        <button
          class="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs transition cursor-pointer"
          @click="handleSearch"
        >
          <Search class="w-3.5 h-3.5" />
          <span>查询</span>
        </button>

        <button
          class="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold flex items-center gap-1.5 transition cursor-pointer border border-slate-200"
          @click="handleReset"
        >
          <RotateCcw class="w-3.5 h-3.5" />
          <span>重置</span>
        </button>
      </div>
    </div>

    <!-- Agent Table -->
    <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6">
      <div class="overflow-x-auto">
        <table class="min-w-[1000px] w-full text-left border-collapse text-sm">
          <thead>
            <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
              <!-- 顺序：姓名、工号、角色（只展示中文，枚举值不需要展示）、手机号码、接听方式、登录状态、通话状态、操作 -->
              <th class="py-3.5 px-4 whitespace-nowrap">姓名</th>
              <th class="py-3.5 px-4 whitespace-nowrap">工号</th>
              <th class="py-3.5 px-4 whitespace-nowrap">角色</th>
              <th class="py-3.5 px-4 whitespace-nowrap">手机号码</th>
              <th class="py-3.5 px-4 whitespace-nowrap">接听方式</th>
              <th class="py-3.5 px-4 whitespace-nowrap">登录状态</th>
              <th class="py-3.5 px-4 whitespace-nowrap">通话状态</th>
              <th class="py-3.5 px-4 text-right whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="loading && agents.length === 0">
              <td colspan="8" class="py-12 text-center text-slate-400">
                <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
                正在加载坐席人员档案...
              </td>
            </tr>
            <tr v-else-if="agents.length === 0">
              <td colspan="8" class="py-12 text-center text-slate-400 font-medium">
                暂无符合条件的坐席人员档案
              </td>
            </tr>
            <tr v-for="ag in agents" :key="ag.id" class="hover:bg-slate-50/80 transition-colors">
              <!-- 1. 姓名 -->
              <td class="py-4 px-4 whitespace-nowrap">
                <div class="flex items-center space-x-3">
                  <div
                    class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold"
                    :class="(ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? 'bg-amber-100 text-amber-700 border border-amber-200' : 'bg-brand-50 text-brand-600 border border-brand-200'"
                  >
                    {{ (ag.agentName || ag.realName || '').slice(0, 1) }}
                  </div>
                  <div class="font-bold text-slate-900 flex items-center gap-1.5 text-sm">
                    <span>{{ ag.agentName || ag.realName }}</span>
                    <Award v-if="ag.roleCode === 'SUPERVISOR' || ag.isSupervisor" class="w-4 h-4 text-amber-500" title="班长主管" />
                  </div>
                </div>
              </td>

              <!-- 2. 工号 -->
              <td class="py-4 px-4 font-mono text-xs font-bold text-slate-800 whitespace-nowrap">
                {{ ag.workNo }}
              </td>

              <!-- 3. 角色 (只展示中文，枚举值不需要展示) -->
              <td class="py-4 px-4 whitespace-nowrap">
                <span
                  class="px-2.5 py-0.5 rounded-full text-xs font-bold border"
                  :class="(ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-indigo-50 text-brand-600 border border-brand-200'"
                >
                  {{ ag.roleName || ((ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? '班长主管' : '标准坐席') }}
                </span>
              </td>

              <!-- 4. 手机号码 -->
              <td class="py-4 px-4 font-mono text-xs text-slate-600 whitespace-nowrap">
                {{ ag.phoneNumber || '-' }}
              </td>

              <!-- 5. 接听方式 -->
              <td class="py-4 px-4 whitespace-nowrap">
                <div class="flex items-center space-x-2">
                  <span
                    class="text-xs font-mono px-2 py-0.5 rounded-md font-bold"
                    :class="ag.boundEndpointType === 'WEBRTC' ? 'text-purple-700 bg-purple-50 border border-purple-200' : ag.boundEndpointType === 'SIP' ? 'text-blue-700 bg-blue-50 border border-blue-200' : 'text-emerald-700 bg-emerald-50 border border-emerald-200'"
                  >
                    {{ ag.boundEndpointType === 'WEBRTC' ? '💻 软电话' : ag.boundEndpointType === 'SIP' ? '☎️ SIP话机' : ag.boundEndpointType === 'MOBILE' ? '📱 随行手机' : '未配置' }}
                  </span>
                  <span v-if="ag.currentExtension || ag.workNo" class="px-2 py-0.5 rounded-lg font-mono text-xs bg-slate-100 text-slate-700 border border-slate-200 flex items-center gap-1">
                    <PhoneCall class="w-3 h-3 text-brand-600" />
                    {{ ag.currentExtension || ag.workNo }}
                  </span>
                </div>
              </td>

              <!-- 6. 登录状态 -->
              <td class="py-4 px-4 whitespace-nowrap">
                <span
                  v-if="ag.isLoggedIn || ag.loginStatus === 'ONLINE'"
                  class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200"
                >
                  <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                  在线
                </span>
                <span
                  v-else
                  class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-500 border border-slate-200"
                >
                  <span class="w-2 h-2 rounded-full bg-slate-400"></span>
                  离线
                </span>
              </td>

              <!-- 7. 通话状态 -->
              <td class="py-4 px-4 whitespace-nowrap">
                <span
                  v-if="!(ag.isLoggedIn || ag.loginStatus === 'ONLINE')"
                  class="text-xs text-slate-400 font-mono"
                >
                  -
                </span>
                <span
                  v-else
                  class="px-2.5 py-0.5 rounded-full text-xs font-bold border"
                  :class="callStatusConfig[ag.callStatus || 'READY']?.class || 'bg-slate-100 text-slate-700 border-slate-200'"
                >
                  {{ ag.callStatusDesc || callStatusConfig[ag.callStatus || 'READY']?.label || '空闲' }}
                </span>
              </td>

              <!-- 8. 操作 (编辑、重置密码、下线[已登录显示]) -->
              <td class="py-4 px-4 text-right whitespace-nowrap">
                <div class="flex items-center justify-end gap-2">
                  <button
                    @click="openEditModal(ag)"
                    class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="修改坐席资料"
                  >
                    <Pencil class="w-3.5 h-3.5 text-slate-500" />
                    <span>编辑</span>
                  </button>

                  <button
                    @click="openResetPasswordModal(ag)"
                    class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="重置登录口令"
                  >
                    <KeyRound class="w-3.5 h-3.5 text-slate-500" />
                    <span>重置密码</span>
                  </button>

                  <button
                    v-if="ag.isLoggedIn || ag.loginStatus === 'ONLINE'"
                    @click="handleForceLogout(ag)"
                    class="px-2.5 py-1.5 bg-amber-50 hover:bg-amber-100 text-amber-700 border border-amber-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="强制下线"
                  >
                    <LogOut class="w-3.5 h-3.5 text-amber-600" />
                    <span>下线</span>
                  </button>

                  <button
                    @click="handleDeleteAgent(ag)"
                    class="px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="彻底注销并删除坐席档案"
                  >
                    <Trash2 class="w-3.5 h-3.5 text-rose-600" />
                    <span>删除</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Footer -->
      <div
        class="mt-5 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500 pt-4 border-t border-slate-100"
      >
        <span class="font-medium">
          共 <strong class="text-slate-800">{{ total }}</strong> 个坐席
        </span>
        <FccPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- Create Agent Modal -->
    <div v-if="isCreateModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <UserPlus class="w-5 h-5 text-brand-600" />
            录入坐席人员
          </h3>
          <button @click="isCreateModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div v-if="formError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 flex items-center gap-2 text-xs text-rose-600 font-bold">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          <span>{{ formError }}</span>
        </div>

        <div class="space-y-3.5 text-xs">
          <div>
            <label class="block text-slate-700 font-bold mb-1">坐席姓名</label>
            <input
              v-model="newAgent.agentName"
              type="text"
              placeholder="例如: 张三 / 钱丁君"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">坐席工号 (数字编号)</label>
            <input
              v-model="newAgent.workNo"
              type="text"
              placeholder="例如: 90101 / 901001"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">坐席手机号</label>
            <input
              v-model="newAgent.phoneNumber"
              type="text"
              placeholder="例如: 13800000001"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">
              初始登录口令
              <span class="font-normal text-slate-400">（选填，留空则系统自动生成随机安全口令）</span>
            </label>
            <input
              v-model="newAgent.password"
              type="text"
              placeholder="例如: 8~64位数字/字母，留空系统随机生成"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">权限角色分配</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                @click="newAgent.roleCode = 'SUPERVISOR'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="newAgent.roleCode === 'SUPERVISOR' ? 'bg-amber-50 border-amber-300 text-amber-700' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                SUPERVISOR 班长主管
              </button>
              <button
                type="button"
                @click="newAgent.roleCode = 'AGENT'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="newAgent.roleCode === 'AGENT' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                AGENT 普通坐席
              </button>
            </div>
            <p class="text-xs text-slate-400 mt-1">班长主管能力由后端角色权限控制，不与具体工号绑定。</p>
          </div>
        </div>

        <div class="pt-3 border-t border-slate-100 flex justify-end space-x-2">
          <button @click="isCreateModalOpen = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer">
            取消
          </button>
          <button
            @click="handleCreateAgent"
            :disabled="submitting"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition disabled:opacity-50 cursor-pointer"
          >
            <span v-if="submitting">正在保存...</span>
            <span v-else>确认录入</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Edit Agent Modal -->
    <div v-if="isEditModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <Pencil class="w-5 h-5 text-brand-600" />
            修改坐席基础资料
          </h3>
          <button @click="isEditModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div v-if="formError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 flex items-center gap-2 text-xs text-rose-600 font-bold">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          <span>{{ formError }}</span>
        </div>

        <div class="space-y-3.5 text-xs">
          <!-- 坐席工号：不可修改 -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="block text-slate-700 font-bold">坐席工号</label>
              <span class="text-[11px] text-slate-400 font-normal">不可修改 (系统唯一登录工号)</span>
            </div>
            <input
              :value="editingAgent?.workNo"
              disabled
              type="text"
              class="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-xl text-slate-500 font-mono cursor-not-allowed"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">坐席姓名 *</label>
            <input
              v-model="editForm.agentName"
              type="text"
              placeholder="坐席真实姓名"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">坐席手机号</label>
            <input
              v-model="editForm.phoneNumber"
              type="text"
              placeholder="联系手机号码"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">权限角色分配</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                @click="editForm.roleCode = 'SUPERVISOR'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="editForm.roleCode === 'SUPERVISOR' ? 'bg-amber-50 border-amber-300 text-amber-700' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                SUPERVISOR 班长主管
              </button>
              <button
                type="button"
                @click="editForm.roleCode = 'AGENT'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="editForm.roleCode === 'AGENT' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                AGENT 标准坐席
              </button>
            </div>
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">账号状态</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                @click="editForm.status = 'ENABLED'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="editForm.status === 'ENABLED' ? 'bg-emerald-50 border-emerald-300 text-emerald-700' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                ● 启用在职
              </button>
              <button
                type="button"
                @click="editForm.status = 'DISABLED'"
                class="py-2.5 px-3 rounded-xl border text-center font-bold transition cursor-pointer"
                :class="editForm.status === 'DISABLED' ? 'bg-rose-50 border-rose-300 text-rose-700' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                ○ 禁用停用
              </button>
            </div>
          </div>

          <div class="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80 text-[11px] text-slate-500">
            💡 提示：如需重置或修改坐席登录密码，请点击列表中的「重置口令」。
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            @click="isEditModalOpen = false"
            class="px-4 py-2 text-slate-600 hover:text-slate-800 font-bold cursor-pointer transition text-xs"
          >
            取消
          </button>
          <button
            type="button"
            @click="handleConfirmEditAgent"
            :disabled="submitting"
            class="px-5 py-2.5 bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white rounded-xl font-bold shadow-xs transition cursor-pointer text-xs"
          >
            {{ submitting ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Binding Modal -->
    <div v-if="isBindingModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <Link2 class="w-5 h-5 text-brand-600" />
            为 {{ currentAgentForBinding?.agentName || currentAgentForBinding?.realName }} 切换当前接听终端
          </h3>
          <button @click="isBindingModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div v-if="formError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 flex items-center gap-2 text-xs text-rose-600 font-bold">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          <span>{{ formError }}</span>
        </div>

        <div class="space-y-3.5 text-xs">
          <div>
            <label class="block text-slate-700 font-bold mb-1">主接听终端方式</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                @click="bindingForm.endpointType = 'WEBRTC'; bindingForm.endpointValue = undefined"
                class="py-2 px-2 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="bindingForm.endpointType === 'WEBRTC' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                💻 软话机
              </button>
              <button
                type="button"
                @click="bindingForm.endpointType = 'SIP'; bindingForm.endpointValue = availableSipExtensions[0]"
                class="py-2 px-2 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="bindingForm.endpointType === 'SIP' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                ☎️ 实体话机
              </button>
            </div>
          </div>

          <!-- 动态输入项 -->
          <div v-if="bindingForm.endpointType === 'WEBRTC'">
            <label class="block text-slate-700 font-bold mb-1">WebRTC 软电话注册工号</label>
            <input
              type="text"
              :value="currentAgentForBinding?.workNo"
              disabled
              class="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-xl text-slate-600 font-mono opacity-80 cursor-not-allowed font-bold"
            />
            <p class="text-xs text-slate-400 mt-1">坐席登录客户端后，使用工号直接向 FreeSWITCH 注册 WebRTC 软电话</p>
          </div>

          <div v-else-if="bindingForm.endpointType === 'SIP'">
            <label class="block text-slate-700 font-bold mb-1">选择绑定的工位硬件分机</label>
            <select
              v-model="bindingForm.endpointValue"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono focus:outline-none focus:ring-2 focus:ring-brand-500 font-bold"
            >
              <option v-for="extension in availableSipExtensions" :key="extension" :value="extension">
                分机 {{ extension }}
              </option>
            </select>
              <p class="text-xs text-slate-400 mt-1">只有已由话机拨 0000 完成绑定的分机才能在这里切换</p>
          </div>

          <p v-if="bindingForm.endpointType === 'WEBRTC'" class="text-xs text-slate-400 mt-1">WebRTC 使用坐席工号登录工作台并注册。</p>
        </div>

        <div class="pt-3 border-t border-slate-100 flex justify-end space-x-2">
          <button @click="isBindingModalOpen = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer">
            取消
          </button>
          <button
            @click="handleBindEndpoint"
            :disabled="submitting"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition disabled:opacity-50 cursor-pointer"
          >
            <span v-if="submitting">正在切换...</span>
            <span v-else>确认切换</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Reset Password Modal (重置密码弹窗) -->
    <div v-if="isResetModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <KeyRound class="w-5 h-5 text-brand-600" />
            重置坐席登录口令
          </h3>
          <button @click="isResetModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div v-if="resetError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 flex items-center gap-2 text-xs text-rose-600 font-bold">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          <span>{{ resetError }}</span>
        </div>

        <div class="space-y-3.5 text-xs">
          <div class="p-3 bg-slate-50 border border-slate-200 rounded-2xl flex items-center justify-between text-xs">
            <div>
              <span class="text-slate-400">目标坐席：</span>
              <strong class="text-slate-800 font-bold">{{ currentAgentForReset?.agentName || currentAgentForReset?.realName }}</strong>
            </div>
            <div>
              <span class="text-slate-400">工号：</span>
              <span class="font-mono font-bold text-slate-800">{{ currentAgentForReset?.workNo }}</span>
            </div>
          </div>

          <div>
            <label class="block text-slate-700 font-bold mb-1">
              新登录口令
              <span class="font-normal text-slate-400">（选填，留空则由系统自动生成随机安全口令）</span>
            </label>
            <input
              v-model="resetPasswordInput"
              type="text"
              placeholder="例如: 8~64位数字/字母，留空系统随机生成"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 font-medium"
            />
            <p class="text-[11px] text-slate-400 mt-1">注意：重置仅更改坐席在工作台网页的登录口令，底层的分机通话不受影响。</p>
          </div>
        </div>

        <div class="pt-3 border-t border-slate-100 flex justify-end space-x-2">
          <button @click="isResetModalOpen = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition cursor-pointer">
            取消
          </button>
          <button
            @click="handleConfirmResetPassword"
            :disabled="resetSubmitting"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition disabled:opacity-50 cursor-pointer"
          >
            <span v-if="resetSubmitting">正在重置...</span>
            <span v-else>确认重置</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Credential Delivery Modal (凭据交付模态框) -->
    <div v-if="isCredentialModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <ShieldCheck class="w-5 h-5 text-emerald-600" />
            坐席登录凭据交付
          </h3>
          <button @click="isCredentialModalOpen = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold cursor-pointer">&times;</button>
        </div>

        <div class="p-3.5 rounded-2xl bg-emerald-50/80 border border-emerald-200/80 text-xs text-emerald-800 space-y-1">
          <p class="font-bold flex items-center gap-1.5 text-emerald-900">
            <CheckCircle class="w-4 h-4 text-emerald-600" />
            凭据已生成，请立即记录并交付坐席本人！
          </p>
          <p class="text-[11px] text-emerald-700">出于安全规范，服务端仅保存单向加盐哈希，关闭此窗口后将无法再次查看该明文口令。</p>
        </div>

        <div class="bg-slate-50 border border-slate-200 rounded-2xl p-4 space-y-3 text-xs font-mono">
          <div class="flex justify-between items-center py-1 border-b border-slate-200/60">
            <span class="text-slate-500 font-sans font-medium">坐席登录工号</span>
            <span class="font-bold text-slate-900 text-sm select-all">{{ deliveredCredential?.account }}</span>
          </div>
          <div class="flex justify-between items-center py-1 border-b border-slate-200/60">
            <span class="text-slate-500 font-sans font-medium">坐席姓名</span>
            <span class="font-bold text-slate-800 font-sans">{{ deliveredCredential?.displayName }}</span>
          </div>
          <div class="flex justify-between items-center py-1">
            <span class="text-slate-500 font-sans font-medium">初始登录密码</span>
            <span class="font-bold text-brand-600 text-sm bg-brand-50 px-2.5 py-0.5 rounded-lg border border-brand-200 select-all font-mono">
              {{ deliveredCredential?.initialPassword }}
            </span>
          </div>
        </div>

        <div class="pt-2 flex justify-end gap-2.5">
          <button
            @click="copyCredential"
            class="px-4 py-2 bg-indigo-50 hover:bg-indigo-100 text-brand-600 border border-brand-200 rounded-xl text-xs font-bold flex items-center gap-1.5 transition cursor-pointer"
          >
            <Check v-if="isCopied" class="w-4 h-4 text-emerald-600" />
            <Copy v-else class="w-4 h-4" />
            <span>{{ isCopied ? '已复制到剪贴板' : '一键复制凭据' }}</span>
          </button>
          <button
            @click="isCredentialModalOpen = false"
            class="px-5 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition cursor-pointer"
          >
            我知道了并完成
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
