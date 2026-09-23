<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { agentApi, type AgentVO, type AccountCredentialVO } from '../api/agentApi';
import { Users, UserPlus, Link2, RefreshCw, CheckCircle, AlertCircle, Award, PhoneCall, KeyRound, Copy, Check, ShieldCheck, Pencil } from 'lucide-vue-next';

const agents = ref<AgentVO[]>([]);
const availableSipExtensions = ref<string[]>([]);
const loading = ref(false);
const total = ref(0);
const successNotice = ref('');

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
    const agentsRes = await agentApi.list({ pageNum: 1, pageSize: 50 });
    agents.value = agentsRes.list || [];
    total.value = agentsRes.total || 0;
  } catch (err: any) {
    console.error('Failed to load agents:', err);
  } finally {
    loading.value = false;
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
          坐席人员管理
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
            管理坐席资料和已经验证的接听终端；物理 SIP 绑定由话机拨 0000 完成
        </p>
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

    <!-- Agent Table -->
    <div class="bg-white border border-slate-100 rounded-3xl overflow-hidden shadow-card p-6">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="border-b border-slate-100 text-xs uppercase tracking-wider text-slate-400 font-bold">
              <th class="py-3.5 px-4">坐席姓名与工号</th>
              <th class="py-3.5 px-4">系统权限角色</th>
              <th class="py-3.5 px-4">当前接听终端</th>
              <th class="py-3.5 px-4">账号状态</th>
              <th class="py-3.5 px-4">坐席业务状态</th>
              <th class="py-3.5 px-4 text-right">操作管理</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-sm text-slate-700">
            <tr v-if="loading && agents.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400">
                <RefreshCw class="w-6 h-6 animate-spin mx-auto mb-2 text-brand-500" />
                正在加载坐席人员档案...
              </td>
            </tr>
            <tr v-else-if="agents.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400 font-medium">
                暂无坐席档案，请使用“录入坐席”创建首个坐席
              </td>
            </tr>
            <tr v-for="ag in agents" :key="ag.id" class="hover:bg-slate-50/80 transition-colors">
              <td class="py-4 px-4">
                <div class="flex items-center space-x-3">
                  <div
                    class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold"
                    :class="(ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? 'bg-amber-100 text-amber-700 border border-amber-200' : 'bg-brand-50 text-brand-600 border border-brand-200'"
                  >
                    {{ (ag.agentName || ag.realName || '').slice(0, 1) }}
                  </div>
                  <div>
                    <div class="font-bold text-slate-900 flex items-center gap-1.5 text-sm">
                      <span>{{ ag.agentName || ag.realName }}</span>
                      <Award v-if="ag.roleCode === 'SUPERVISOR' || ag.isSupervisor" class="w-4 h-4 text-amber-500" />
                    </div>
                    <div class="font-mono text-xs text-slate-400">{{ ag.workNo }}</div>
                  </div>
                </div>
              </td>
              <td class="py-4 px-4">
                <span
                  class="px-2.5 py-0.5 rounded-full text-xs font-bold"
                  :class="(ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-indigo-50 text-brand-600 border border-brand-200'"
                >
                  {{ (ag.roleCode === 'SUPERVISOR' || ag.isSupervisor) ? 'SUPERVISOR 班长主管' : 'AGENT 标准坐席' }}
                </span>
              </td>
              <td class="py-4 px-4">
                <div class="flex flex-col gap-1">
                  <div class="flex items-center space-x-2">
                    <span class="px-2.5 py-0.5 rounded-lg font-mono text-xs bg-slate-100 text-slate-800 border border-slate-200 flex items-center gap-1">
                      <PhoneCall class="w-3.5 h-3.5 text-brand-600" />
                      {{ ag.currentExtension || ag.workNo }}
                    </span>
                    <span
                      class="text-xs font-mono px-2 py-0.5 rounded-md font-bold"
                      :class="ag.boundEndpointType === 'WEBRTC' ? 'text-purple-700 bg-purple-50 border border-purple-200' : ag.boundEndpointType === 'SIP' ? 'text-blue-700 bg-blue-50 border border-blue-200' : 'text-emerald-700 bg-emerald-50 border border-emerald-200'"
                    >
                      {{ ag.boundEndpointType === 'WEBRTC' ? '💻 软电话' : ag.boundEndpointType === 'SIP' ? '☎️ SIP话机' : '📱 随行手机' }}
                    </span>
                  </div>
                  <div class="text-xs text-slate-400 font-mono">
                    工号: {{ ag.workNo }} · 手机: {{ ag.phoneNumber || '未设' }}
                  </div>
                </div>
              </td>
              <td class="py-4 px-4">
                <span class="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600">
                  <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
                  {{ ag.status === 'ENABLED' ? '在职生效' : '停用' }}
                </span>
              </td>
              <td class="py-4 px-4">
                <span class="px-2.5 py-0.5 rounded-full text-xs font-mono font-bold bg-slate-100 text-slate-700 border border-slate-200">
                  {{ ag.state || 'OFFLINE' }}
                </span>
              </td>
              <td class="py-4 px-4 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button
                    @click="openEditModal(ag)"
                    class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="修改坐席基础资料"
                  >
                    <Pencil class="w-3.5 h-3.5 text-slate-500" />
                    <span>编辑</span>
                  </button>
                  <button
                    @click="openResetPasswordModal(ag)"
                    class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                    title="重置坐席登录口令"
                  >
                    <KeyRound class="w-3.5 h-3.5 text-slate-500" />
                    <span>重置口令</span>
                  </button>
                  <button
                    @click="openBindingModal(ag)"
                    class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 border border-brand-200 rounded-xl text-xs font-bold flex items-center gap-1 transition cursor-pointer"
                  >
                    <Link2 class="w-3.5 h-3.5" />
                    <span>切换终端</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
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
