import { ref, onMounted } from 'vue';
import { useScopeTimers } from '../../../shared/lifecycle/scopeTimers';
import { agentApi, type AgentVO, type AccountCredentialVO } from '../../../api/agentApi';
import { confirmAction, toastSuccess, toastError } from '../../../utils/feedback';

/** Owns the feature state, effects and use cases; the view only composes rendering. */
export function useAgentManagement() {
  const timers = useScopeTimers();
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
      timers.schedule(() => (successNotice.value = ''), 4000);
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
      timers.schedule(() => (isCopied.value = false), 3000);
    } catch {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      isCopied.value = true;
      timers.schedule(() => (isCopied.value = false), 3000);
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
      timers.schedule(() => (successNotice.value = ''), 5000);
    } catch (err: any) {
      formError.value = err.message || '绑定失败';
    } finally {
      submitting.value = false;
    }
  };

  onMounted(() => {
    loadData();
  });

  return { agents, availableSipExtensions, loading, total, successNotice, pageNum, pageSize, searchAgentName, searchPhoneNumber, searchLoginStatus, dateRange, callStatusConfig, isCreateModalOpen, isEditModalOpen, isBindingModalOpen, isResetModalOpen, isCredentialModalOpen, submitting, formError, newAgent, editingAgent, editForm, deliveredCredential, isCopied, currentAgentForReset, resetPasswordInput, resetSubmitting, resetError, bindingForm, currentAgentForBinding, loadData, handleSearch, handleReset, handlePageChange, handleSizeChange, handleForceLogout, handleDeleteAgent, openCreateModal, handleCreateAgent, openEditModal, handleConfirmEditAgent, openResetPasswordModal, handleConfirmResetPassword, copyCredential, openBindingModal, handleBindEndpoint };
}
