import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AgentStatus, AnswerEndpointType } from '../types/telephony';
import { authApi } from '../api/authApi';
import { fetchAgentEndpoints, switchAgentEndpoint } from '../api/agentApi';

export const useAgentStore = defineStore('agent', () => {
  const token = ref<string | null>(localStorage.getItem('fcc_agent_satoken'));
  const workNo = ref(localStorage.getItem('fcc_agent_workno') || '901001');
  const agentName = ref(localStorage.getItem('fcc_agent_name') || '钱丁君');
  const role = ref(localStorage.getItem('fcc_agent_role') || 'SUPERVISOR');
  const permissions = ref<string[]>([]);
  const status = ref<AgentStatus>('READY');
  const endpoint = ref<AnswerEndpointType>((localStorage.getItem('fcc_agent_endpoint') as AnswerEndpointType) || 'WEBRTC');
  const extension = ref(localStorage.getItem('fcc_agent_extension') || '901001');
  const serviceGroup = ref('客服一组');

  // 三端具体配置
  const boundSipExtension = ref(localStorage.getItem('fcc_bound_sip_extension') || '1007');
  const boundMobile = ref(localStorage.getItem('fcc_bound_mobile') || '13800000001');
  const availableSipExtensions = ref<string[]>(['1007', '1008', '1017']);

  const isLoggedIn = computed(() => !!token.value);
  const isSupervisor = computed(() => role.value === 'SUPERVISOR' || workNo.value === '901001');

  function setStatus(newStatus: AgentStatus) {
    status.value = newStatus;
  }

  function setEndpoint(newEndpoint: AnswerEndpointType) {
    endpoint.value = newEndpoint;
    localStorage.setItem('fcc_agent_endpoint', newEndpoint);
  }

  async function loadEndpoints() {
    if (!token.value || !workNo.value) return;
    try {
      const data = await fetchAgentEndpoints(workNo.value);
      if (data) {
        if (data.activeEndpointType) {
          endpoint.value = data.activeEndpointType as AnswerEndpointType;
          localStorage.setItem('fcc_agent_endpoint', data.activeEndpointType);
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
    let finalValue = targetValue;
    if (!finalValue) {
      if (targetType === 'WEBRTC') finalValue = workNo.value;
      else if (targetType === 'SIP') finalValue = boundSipExtension.value || '1007';
      else if (targetType === 'MOBILE') finalValue = boundMobile.value || '13800000001';
    }

    // 乐观更新本地接听方式，保障即时反馈
    endpoint.value = targetType;
    localStorage.setItem('fcc_agent_endpoint', targetType);

    try {
      const data = await switchAgentEndpoint({
        workNo: workNo.value,
        endpointType: targetType,
        endpointValue: finalValue,
      });

      if (data) {
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
    } catch (e) {
      console.warn('Backend switchAgentEndpoint failed, keeping optimistic state:', e);
    }
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
        endpoint.value = data.endpointType as AnswerEndpointType;
        localStorage.setItem('fcc_agent_endpoint', data.endpointType);
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
    switchEndpoint,
    login,
    logout,
  };
});

