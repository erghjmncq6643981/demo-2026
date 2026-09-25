import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AgentLoginStatus, AgentWorkStatus, AnswerEndpointType } from '../types/telephony';
import { authApi, type AgentEndpointsResp } from '../api/authApi';
import { telephonyApi } from '../api/apiClient';
import { errorText, toastError, toastSuccess } from '../utils/feedback';

interface AgentRuntimeStateResp {
  loginStatus: AgentLoginStatus;
  workStatus: AgentWorkStatus;
  activeCallId?: string;
}

export const useAgentStore = defineStore('agent', () => {
  const token = ref<string | null>(localStorage.getItem('fcc_agent_satoken'));
  const workNo = ref(localStorage.getItem('fcc_agent_workno') || '');
  const agentName = ref(localStorage.getItem('fcc_agent_name') || '');
  const role = ref(localStorage.getItem('fcc_agent_role') || '');
  const permissions = ref<string[]>([]);
  const loginStatus = ref<AgentLoginStatus>('LOGOUT');
  const workStatus = ref<AgentWorkStatus>('UNREADY');
  const runtimeCallId = ref('');
  const presencePending = ref(false);
  const endpoint = ref<AnswerEndpointType>(readEndpoint(localStorage.getItem('fcc_agent_endpoint')));
  const extension = ref(localStorage.getItem('fcc_agent_extension') || '');
  const serviceGroup = ref('');

  // FreeSWITCH WebRTC 接入配置（从 Server 仅拉取一次并持有）
  const sipConfig = ref<{ extension: string; wsUrl: string; domain: string; password: string } | null>(null);
  const sipConfigLoading = ref(false);

  // 三端具体配置
  const webrtcWorkNo = ref(localStorage.getItem('fcc_webrtc_workno') || '');
  const boundSipExtension = ref(localStorage.getItem('fcc_bound_sip_extension') || '');
  const boundMobile = ref(localStorage.getItem('fcc_bound_mobile') || '');
  const availableSipExtensions = ref<string[]>([]);
  const endpointsLoading = ref(false);
  const endpointSwitching = ref(false);
  const endpointError = ref('');

  const isLoggedIn = computed(() => !!token.value);
  const isSupervisor = computed(() => role.value === 'SUPERVISOR');

  async function setLoginStatus(newStatus: Exclude<AgentLoginStatus, 'LOGOUT'>) {
    if (presencePending.value || loginStatus.value === newStatus) return;
    presencePending.value = true;
    try {
      const response = await telephonyApi.post<unknown, { data: AgentRuntimeStateResp }>(
        '/agent-state',
        { status: newStatus },
      );
      applyRuntimeState(response.data);
      toastSuccess(newStatus === 'LOGIN' ? '坐席已示闲，可接来电' : '坐席已示忙，仅允许主动外呼');
    } catch (error) {
      toastError(errorText(error, '工作状态变更失败'));
    } finally {
      presencePending.value = false;
    }
  }

  async function refreshRuntimeState() {
    if (!token.value) return;
    try {
      const response = await telephonyApi.get<unknown, { data: AgentRuntimeStateResp }>('/agent-state');
      applyRuntimeState(response.data);
    } catch { /* Keep the last confirmed state; do not invent READY on failure. */ }
  }

  function applyRuntimeState(data: AgentRuntimeStateResp) {
    loginStatus.value = data.loginStatus;
    workStatus.value = data.workStatus;
    runtimeCallId.value = data.activeCallId || '';
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
    webrtcWorkNo.value = data.webrtcWorkNo || data.workNo || '';
    boundSipExtension.value = data.sipExtension || '';
    boundMobile.value = data.mobilePhone || '';
    availableSipExtensions.value = data.availableSipExtensions || [];
    if (extension.value) localStorage.setItem('fcc_agent_extension', extension.value);
    if (webrtcWorkNo.value) localStorage.setItem('fcc_webrtc_workno', webrtcWorkNo.value);
    if (boundSipExtension.value) localStorage.setItem('fcc_bound_sip_extension', boundSipExtension.value);
    if (boundMobile.value) localStorage.setItem('fcc_bound_mobile', boundMobile.value);
  }

  async function loadEndpoints() {
    if (!token.value || !workNo.value) return;
    endpointsLoading.value = true;
    endpointError.value = '';
    try {
      const response = await authApi.endpoints();
      if (response?.data) applyEndpointData(response.data);
    } catch (err) {
      endpointError.value = errorText(err, '接听方式加载失败');
    } finally {
      endpointsLoading.value = false;
    }
  }

  async function loadSipConfig(force = false) {
    if (!token.value || (sipConfig.value && !force)) return sipConfig.value;
    sipConfigLoading.value = true;
    try {
      const res = await authApi.sipConfig();
      if (res?.data) {
        sipConfig.value = res.data;
        return res.data;
      }
    } catch (err) {
      console.warn('获取本人 SIP 配置失败:', err);
    } finally {
      sipConfigLoading.value = false;
    }
    return null;
  }

  async function switchEndpoint(targetType: AnswerEndpointType, targetValue?: string) {
    if (targetType === 'MOBILE') throw new Error('手机接听尚未实现');
    if (endpointSwitching.value) return;
    endpointSwitching.value = true;
    endpointError.value = '';
    try {
      const response = await authApi.switchEndpoint({ endpointType: targetType, endpointValue: targetValue });
      if (response?.data) applyEndpointData(response.data);
      toastSuccess('接听方式已切换');
      return response?.data;
    } catch (cause) {
      endpointError.value = errorText(cause, '接听方式切换失败');
      throw cause;
    } finally {
      endpointSwitching.value = false;
    }
  }

  async function login(workNumber: string, pass: string) {
    localStorage.removeItem('fcc_agent_satoken');
    token.value = null;

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
      await loadSipConfig();
      await refreshRuntimeState();
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
      sipConfig.value = null;
      loginStatus.value = 'LOGOUT';
      workStatus.value = 'UNREADY';
      runtimeCallId.value = '';
      localStorage.removeItem('fcc_agent_satoken');
      localStorage.removeItem('fcc_agent_workno');
      localStorage.removeItem('fcc_agent_name');
      localStorage.removeItem('fcc_agent_role');
      localStorage.removeItem('fcc_agent_extension');
      localStorage.removeItem('fcc_agent_endpoint');
      localStorage.removeItem('fcc_webrtc_workno');
      localStorage.removeItem('fcc_bound_sip_extension');
      localStorage.removeItem('fcc_bound_mobile');
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('fcc:auth-expired', () => {
      token.value = null;
      sipConfig.value = null;
      loginStatus.value = 'LOGOUT';
      workStatus.value = 'UNREADY';
      runtimeCallId.value = '';
    });
  }

  return {
    token,
    workNo,
    agentName,
    role,
    permissions,
    loginStatus,
    workStatus,
    runtimeCallId,
    presencePending,
    endpoint,
    extension,
    serviceGroup,
    sipConfig,
    sipConfigLoading,
    webrtcWorkNo,
    boundSipExtension,
    boundMobile,
    availableSipExtensions,
    endpointsLoading,
    endpointSwitching,
    endpointError,
    isLoggedIn,
    isSupervisor,
    setLoginStatus,
    setEndpoint,
    loadEndpoints,
    loadSipConfig,
    refreshRuntimeState,
    switchEndpoint,
    login,
    logout,
  };
});

