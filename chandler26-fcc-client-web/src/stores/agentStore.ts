import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AgentStatus, AnswerEndpointType } from '../types/telephony';
import { authApi } from '../api/authApi';
import { fetchAgentEndpoints, switchAgentEndpoint } from '../api/agentApi';
import { telephonyApi } from '../api/apiClient';
import { toastError } from '../utils/feedback';

export const useAgentStore = defineStore('agent', () => {
  const token = ref<string | null>(localStorage.getItem('fcc_agent_satoken'));
  const workNo = ref(localStorage.getItem('fcc_agent_workno') || '');
  const agentName = ref(localStorage.getItem('fcc_agent_name') || '');
  const role = ref(localStorage.getItem('fcc_agent_role') || '');
  const permissions = ref<string[]>([]);
  const status = ref<AgentStatus>('REST');
  const endpoint = ref<AnswerEndpointType>('SIP');
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
    if (newEndpoint !== 'SIP') throw new Error('当前版本仅支持绑定的实体话机');
    endpoint.value = newEndpoint;
    localStorage.setItem('fcc_agent_endpoint', newEndpoint);
  }

  async function loadEndpoints() {
    if (!token.value || !workNo.value) return;
    try {
      const data = await fetchAgentEndpoints(workNo.value);
      if (data) {
        if (data.activeEndpointType) {
          endpoint.value = 'SIP';
          localStorage.setItem('fcc_agent_endpoint', 'SIP');
        }
        if (data.activeEndpointValue) {
          extension.value = data.activeEndpointValue;
          localStorage.setItem('fcc_agent_extension', data.activeEndpointValue);
        }
        if (data.sipExtension) {
          boundSipExtension.value = data.sipExtension;
          localStorage.setItem('fcc_bound_sip_extension', data.sipExtension);
        }
        if (data.mobilePhone) {
          boundMobile.value = data.mobilePhone;
          localStorage.setItem('fcc_bound_mobile', data.mobilePhone);
        }
        if (data.availableSipExtensions && data.availableSipExtensions.length > 0) {
          availableSipExtensions.value = data.availableSipExtensions;
        }
      }
    } catch (err) {
      console.warn('加载坐席三端配置失败:', err);
    }
  }

  async function switchEndpoint(targetType: AnswerEndpointType, targetValue?: string) {
    if (targetType !== 'SIP') throw new Error('当前版本仅支持绑定的实体话机');
    let finalValue = targetValue;
    if (!finalValue) {
      finalValue = boundSipExtension.value || extension.value;
    }
    if (!finalValue) throw new Error('当前接听方式没有可用的已绑定终端');

    const data = await switchAgentEndpoint({
      workNo: workNo.value,
      endpointType: targetType,
      endpointValue: finalValue,
    });

    if (data) {
      endpoint.value = targetType;
      localStorage.setItem('fcc_agent_endpoint', targetType);
      extension.value = data.activeEndpointValue;
      localStorage.setItem('fcc_agent_extension', data.activeEndpointValue);

      if (data.sipExtension) {
        boundSipExtension.value = data.sipExtension;
        localStorage.setItem('fcc_bound_sip_extension', data.sipExtension);
      }
      if (data.mobilePhone) {
        boundMobile.value = data.mobilePhone;
        localStorage.setItem('fcc_bound_mobile', data.mobilePhone);
      }
    }
    return data;
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
      if (data.endpointType) {
        endpoint.value = 'SIP';
        localStorage.setItem('fcc_agent_endpoint', 'SIP');
      }

      localStorage.setItem('fcc_agent_satoken', data.tokenValue);
      localStorage.setItem('fcc_agent_workno', data.loginId);
      localStorage.setItem('fcc_agent_name', data.realName);
      localStorage.setItem('fcc_agent_role', data.role);

      // 异步加载三端配置
      loadEndpoints().catch(() => {});
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

