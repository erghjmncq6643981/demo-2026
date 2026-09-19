<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { agentApi, type AgentVO, type WhitelistItem, type AgentBindingReq } from '../api/agentApi';
import { extensionApi, type ExtensionVO } from '../features/extensions/api/extensionApi';
import { Users, UserPlus, Link2, ShieldCheck, RefreshCw, CheckCircle, AlertCircle, Award, PhoneCall } from 'lucide-vue-next';

const agents = ref<AgentVO[]>([]);
const whitelist = ref<WhitelistItem[]>([]);
const availableExtensions = ref<ExtensionVO[]>([]);
const loading = ref(false);
const total = ref(0);
const successNotice = ref('');

// Modals
const isCreateModalOpen = ref(false);
const isBindingModalOpen = ref(false);
const submitting = ref(false);
const formError = ref('');

// New Agent Form
const newAgent = ref({
  workNo: '',
  agentName: '',
  roleCode: 'AGENT',
  phoneNumber: '',
});

// Binding Form
const bindingForm = ref<AgentBindingReq>({
  agentId: '',
  endpointType: 'WEBRTC',
  endpointValue: '',
  priority: 0,
});
const currentAgentForBinding = ref<AgentVO | null>(null);

const loadData = async () => {
  loading.value = true;
  try {
    const [whitelistRes, agentsRes, extRes] = await Promise.all([
      agentApi.getWhitelist(),
      agentApi.list({ pageNum: 1, pageSize: 50 }),
      extensionApi.list({ pageNum: 1, pageSize: 50 }),
    ]);
    whitelist.value = whitelistRes || [];
    agents.value = agentsRes.list || [];
    total.value = agentsRes.total || 0;
    availableExtensions.value = extRes.list || [];
  } catch (err: any) {
    console.error('Failed to load agents:', err);
  } finally {
    loading.value = false;
  }
};

const handleSelectWhitelist = (item: WhitelistItem) => {
  newAgent.value.workNo = item.defaultWorkNo;
  newAgent.value.agentName = item.realName;
  newAgent.value.roleCode = item.isSupervisor ? 'SUPERVISOR' : 'AGENT';
};

const openCreateModal = () => {
  newAgent.value = {
    workNo: '',
    agentName: '',
    roleCode: 'AGENT',
    phoneNumber: '',
  };
  formError.value = '';
  isCreateModalOpen.value = true;
};

const handleCreateAgent = async () => {
  if (!newAgent.value.workNo || !newAgent.value.agentName) {
    formError.value = '请完整填写工号与姓名';
    return;
  }
  submitting.value = true;
  formError.value = '';
  try {
    await agentApi.create({
      workNo: newAgent.value.workNo,
      agentName: newAgent.value.agentName,
      phoneNumber: newAgent.value.phoneNumber,
      roleCode: newAgent.value.roleCode,
    });
    successNotice.value = `坐席 ${newAgent.value.agentName} (${newAgent.value.workNo}) 录入成功！`;
    isCreateModalOpen.value = false;
    await loadData();
    setTimeout(() => (successNotice.value = ''), 5000);
  } catch (err: any) {
    formError.value = err.message || '录入失败，请检查工号或联系管理员';
  } finally {
    submitting.value = false;
  }
};

const openBindingModal = (agent: AgentVO) => {
  currentAgentForBinding.value = agent;
  const initialType = agent.boundEndpointType || 'WEBRTC';
  let initialValue = agent.currentExtension || agent.boundExtension;
  if (initialType === 'WEBRTC') {
    initialValue = agent.workNo;
  } else if (initialType === 'MOBILE') {
    initialValue = agent.phoneNumber;
  }

  bindingForm.value = {
    agentId: agent.id,
    endpointType: initialType,
    endpointValue: initialValue || '',
    priority: 0,
  };
  formError.value = '';
  isBindingModalOpen.value = true;
};

const handleBindEndpoint = async () => {
  if (bindingForm.value.endpointType === 'WEBRTC') {
    bindingForm.value.endpointValue = currentAgentForBinding.value?.workNo || '';
  }
  if (!bindingForm.value.endpointValue) {
    formError.value = '请选择或输入要绑定的分机或手机号';
    return;
  }
  submitting.value = true;
  formError.value = '';
  try {
    await agentApi.bindEndpoint({
      agentId: bindingForm.value.agentId,
      endpointType: bindingForm.value.endpointType,
      endpointValue: bindingForm.value.endpointValue,
      priority: 0,
    });
    successNotice.value = `坐席 ${currentAgentForBinding.value?.agentName || currentAgentForBinding.value?.realName} 已成功设置主接听终端为 ${bindingForm.value.endpointValue} (${bindingForm.value.endpointType})！`;
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
          坐席人员管理与白名单准入
        </h2>
        <p class="text-xs text-slate-400 mt-0.5">
          严格落实呼叫中心 9 人白名单安全准入机制，可对坐席授权班长主管（SUPERVISOR）并绑定通信分机
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
          <span>录入坐席 (白名单)</span>
        </button>
      </div>
    </div>

    <!-- Whitelist Horizontal Badges -->
    <div class="bg-white border border-slate-100 rounded-3xl p-5 shadow-card">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center space-x-2">
          <ShieldCheck class="w-4 h-4 text-emerald-600" />
          <span class="text-xs font-black text-slate-800 uppercase tracking-wider">法定 9 人团队白名单一览</span>
        </div>
        <span class="text-xs text-slate-400">点击白名单成员可快捷预填录入</span>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-9 gap-2.5">
        <div
          v-for="item in whitelist"
          :key="item.defaultWorkNo"
          @click="handleSelectWhitelist(item); isCreateModalOpen = true;"
          class="p-3 rounded-2xl border bg-slate-50/80 hover:bg-indigo-50/60 hover:border-brand-300 cursor-pointer transition flex flex-col items-center text-center group shadow-2xs"
          :class="item.isSupervisor ? 'border-amber-300 bg-amber-50/30' : 'border-slate-200/80'"
        >
          <div
            class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold mb-1 shadow-xs"
            :class="item.isSupervisor ? 'bg-gradient-to-tr from-amber-500 to-orange-500 text-white' : 'bg-white text-slate-700 border border-slate-200 group-hover:bg-brand-500 group-hover:text-white'"
          >
            {{ item.realName.slice(0, 1) }}
          </div>
          <span class="text-xs font-bold text-slate-800 group-hover:text-brand-600">{{ item.realName }}</span>
          <span class="font-mono text-xs text-slate-400">{{ item.defaultWorkNo }}</span>
          <span v-if="item.isSupervisor" class="mt-1 text-[11px] px-1.5 py-0.2 bg-amber-100 text-amber-700 rounded font-bold border border-amber-200">
            班长主管
          </span>
        </div>
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
              <th class="py-3.5 px-4">绑定分机 / 终端</th>
              <th class="py-3.5 px-4">账号状态</th>
              <th class="py-3.5 px-4">坐席业务状态</th>
              <th class="py-3.5 px-4 text-right">分机绑定与配置</th>
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
                暂无坐席档案，请点击上方白名单成员快速录入
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
                <button
                  @click="openBindingModal(ag)"
                  class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-brand-600 border border-brand-200 rounded-xl text-xs font-bold flex items-center gap-1 ml-auto transition cursor-pointer"
                >
                  <Link2 class="w-3.5 h-3.5" />
                  <span>绑定分机</span>
                </button>
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
            <p class="text-xs text-slate-400 mt-1">注：仅 901001 钱丁君具备班长主管干预调度权限</p>
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

    <!-- Binding Modal -->
    <div v-if="isBindingModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
      <div class="w-full max-w-md bg-white border border-slate-100 rounded-3xl p-6 shadow-popover space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-black text-slate-900 flex items-center gap-2">
            <Link2 class="w-5 h-5 text-brand-600" />
            为 {{ currentAgentForBinding?.agentName || currentAgentForBinding?.realName }} 绑定通信分机
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
            <div class="grid grid-cols-3 gap-2">
              <button
                type="button"
                @click="bindingForm.endpointType = 'WEBRTC'; bindingForm.endpointValue = currentAgentForBinding?.workNo || ''"
                class="py-2 px-2 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="bindingForm.endpointType === 'WEBRTC' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                💻 软话机
              </button>
              <button
                type="button"
                @click="bindingForm.endpointType = 'SIP'; bindingForm.endpointValue = '1007'"
                class="py-2 px-2 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="bindingForm.endpointType === 'SIP' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                ☎️ 实体话机
              </button>
              <button
                type="button"
                @click="bindingForm.endpointType = 'MOBILE'; bindingForm.endpointValue = currentAgentForBinding?.phoneNumber || '13800000001'"
                class="py-2 px-2 rounded-xl border text-center font-bold transition cursor-pointer text-xs"
                :class="bindingForm.endpointType === 'MOBILE' ? 'bg-indigo-50 border-brand-300 text-brand-600' : 'bg-slate-50 border-slate-200 text-slate-600'"
              >
                📱 随行手机
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
              <option v-for="ext in availableExtensions" :key="ext.id" :value="ext.extension">
                分机 {{ ext.extension }} ({{ ext.endpointType }} - {{ ext.onlineStatus }})
              </option>
            </select>
            <p class="text-xs text-slate-400 mt-1">工位硬件桌面电话机或 MicroSIP / Linphone 注册此分机号</p>
          </div>

          <div v-else-if="bindingForm.endpointType === 'MOBILE'">
            <label class="block text-slate-700 font-bold mb-1">代接移动手机号码</label>
            <input
              v-model="bindingForm.endpointValue"
              type="tel"
              placeholder="请输入11位手机号码"
              class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 font-mono focus:outline-none focus:ring-2 focus:ring-brand-500 font-bold"
            />
            <p class="text-xs text-slate-400 mt-1">来电将通过运营商 PSTN / 移动中继转接至该手机号</p>
          </div>
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
            <span v-if="submitting">正在绑定...</span>
            <span v-else>确认绑定</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
