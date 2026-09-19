import { adminApi } from './apiClient';

export interface WhitelistMember {
  realName: string;
  desc: string;
  defaultWorkNo: string;
  isSupervisor: boolean;
}

export interface AgentEndpointsVO {
  workNo: string;
  agentName: string;
  activeEndpointType: 'WEBRTC' | 'SIP' | 'MOBILE';
  activeEndpointValue: string;
  webrtcWorkNo: string;
  sipExtension: string;
  mobilePhone: string;
  availableSipExtensions: string[];
}

export interface SwitchEndpointReq {
  workNo: string;
  endpointType: 'WEBRTC' | 'SIP' | 'MOBILE';
  endpointValue?: string;
}

export interface CommonResult<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * 获取系统法定 9 人坐席白名单
 */
export async function fetchAgentWhitelist(): Promise<WhitelistMember[]> {
  const res = (await adminApi.get('/agents/whitelist')) as unknown as CommonResult<WhitelistMember[]>;
  return res.data || [];
}

/**
 * 获取坐席三端配置详情
 */
export async function fetchAgentEndpoints(workNo: string): Promise<AgentEndpointsVO> {
  const res = (await adminApi.get(`/agents/${workNo}/endpoints`)) as unknown as CommonResult<AgentEndpointsVO>;
  return res.data;
}

/**
 * 切换坐席接听方式与终端
 */
export async function switchAgentEndpoint(req: SwitchEndpointReq): Promise<AgentEndpointsVO> {
  const res = (await adminApi.post('/agents/switch-endpoint', req)) as unknown as CommonResult<AgentEndpointsVO>;
  return res.data;
}

