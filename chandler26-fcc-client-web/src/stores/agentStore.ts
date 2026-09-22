import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AgentStatus, AnswerEndpointType } from '../types/telephony';
import { authApi, type AgentEndpointsResp } from '../api/authApi';
import { telephonyApi } from '../api/apiClient';
import { toastError } from '../utils/feedback';

export const useAgentStore = defineStore('agent', () => {
  const token = ref<string | null>(localStorage.getItem('fcc_agent_satoken'));
  const workNo = ref(localStorage.getItem('fcc_agent_workno') || '');
  const agentName = ref(localStorage.getItem('fcc_agent_name') || '');
  const role = ref(localStorage.getItem('fcc_agent_role') || '');
  const permissions = ref<string[]>([]);
  const status = ref<AgentStatus>('REST');
  const endpoint = ref<AnswerEndpointType>(readEndpoint(localStorage.getItem('fcc_agent_endpoint')));
  const extension = ref(localStorage.getItem('fcc_agent_extension') || '');
  const serviceGroup = ref('');

  // 三端具体配置
  const boundSipExtension = ref(localStorage.getItem('fcc_bound_sip_extension') || '');
  const boundMobile = ref(localStorage.getItem('fcc_bound_mobile') || '');
  const availableSipExtensions = ref<string[]>([]);

  const isLoggedIn = computed(() => !!token.value);
  const isSupervisor = computed(() => role.value === 'SUPERVISOR');

  async function setStatus(newStatus: AgentStatus) {
    try {
      const response = await telephonyApi.post<unknown, { data: { status: AgentStatus } }>('/agent-state', { status: newStatus });
      status.value = response.data.status;
    } catch (error) { toastError(error instanceof Error ? error.message : '状态变更失败'); }
  }

  async function refreshStatus() {
    if (!token.value) return;
    try {
      const response = await telephonyApi.get<unknown, { data: { status: AgentStatus } }>('/agent-state');
      status.value = response.data.status;
    } catch { /* Keep the last confirmed state; do not invent READY on failure. */ }
  }

  function setEndpoint(newEndpoint: AnswerEndpointType) {
    endpoint.value = newEndpoint;
    localStorage.setItem('fcc_agent_endpoint', newEndpoint);
  }

  function readEndpoint(value: string | null): AnswerEndpointType {
    return value === 'WEBRTC' || value === 'SIP' || value === 'MOBILE' ? value : 'WEBRTC';
  }

  function applyEndpointData(data: AgentEndpointsResp) {
    endpoint.value = readEndpoint(data.activeEndpointType);
    localStorage.setItem('fcc_agent_endpoint', endpoint.value);
    extension.value = data.activeEndpointValue || '';
    boundSipExtension.value = data.sipExtension || '';
    boundMobile.value = data.mobilePhone || '';
    availableSipExtensions.value = data.availableSipExtensions || [];
    if (extension.value) localStorage.setItem('fcc_agent_extension', extension.value);
    if (boundSipExtension.value) localStorage.setItem('fcc_bound_sip_extension', boundSipExtension.value);
    if (boundMobile.value) localStorage.setItem('fcc_bound_mobile', boundMobile.value);
  }

  async function loadEndpoints() {
    if (!token.value || !workNo.value) return;
    try {
      const response = await authApi.endpoints();
      if (response?.data) applyEndpointData(response.data);
    } catch (err) {
      console.warn('加载坐席三端配置失败:', err);
    }
  }

  async function switchEndpoint(targetType: AnswerEndpointType, targetValue?: string) {
    if (targetType === 'MOBILE') throw new Error('手机接听尚未实现');
    const response = await authApi.switchEndpoint({ endpointType: targetType, endpointValue: targetValue });
    if (response?.data) applyEndpointData(response.data);
    return response?.data;
  }

  async function login(workNumber: string, pass: string) {
    const res = await authApi.login({
      username: workNumber,
      password: pass,
      loginType: 'AGENT',
    });
    if (res && res.code === 200 && res.data) {
      const data = res.data;
      token.value = data.tokenValue;
      workNo.value = data.loginId;
      agentName.value = data.realName;
      role.value = data.role;
      permissions.value = data.permissions || [];
      if (data.extension) {
        extension.value = data.extension;
        localStorage.setItem('fcc_agent_extension', data.extension);
      }
      if (data.endpointType) setEndpoint(readEndpoint(data.endpointType));

      localStorage.setItem('fcc_agent_satoken', data.tokenValue);
      localStorage.setItem('fcc_agent_workno', data.loginId);
      localStorage.setItem('fcc_agent_name', data.realName);
      localStorage.setItem('fcc_agent_role', data.role);

      await loadEndpoints();
      return data;
    } else {
      throw new Error(res?.message || '登录失败');
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await authApi.logout();
      }
    } catch {
      // ignore
    } finally {
      token.value = null;
      localStorage.removeItem('fcc_agent_satoken');
      localStorage.removeItem('fcc_agent_workno');
      localStorage.removeItem('fcc_agent_name');
      localStorage.removeItem('fcc_agent_role');
      localStorage.removeItem('fcc_agent_extension');
      localStorage.removeItem('fcc_agent_endpoint');
      localStorage.removeItem('fcc_bound_sip_extension');
      localStorage.removeItem('fcc_bound_mobile');
    }
  }

  return {
    token,
    workNo,
    agentName,
    role,
    permissions,
    status,
    endpoint,
    extension,
    serviceGroup,
    boundSipExtension,
    boundMobile,
    availableSipExtensions,
    isLoggedIn,
    isSupervisor,
    setStatus,
    setEndpoint,
    loadEndpoints,
    refreshStatus,
    switchEndpoint,
    login,
    logout,
  };
});

