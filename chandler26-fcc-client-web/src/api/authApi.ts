import { adminApi } from './apiClient';

export interface AgentEndpointsResp {
  workNo: string;
  agentName: string;
  activeEndpointType: 'WEBRTC' | 'SIP' | 'MOBILE';
  activeEndpointValue: string;
  webrtcWorkNo?: string;
  sipExtension?: string;
  mobilePhone?: string;
  availableSipExtensions: string[];
}

export interface AgentLoginReq {
  username: string;
  password: string;
  loginType?: string;
}

export interface AgentLoginRespVO {
  tokenValue: string;
  tokenName: string;
  loginId: string;
  realName: string;
  role: string;
  permissions: string[];
  extension?: string;
  endpointType?: string;
}

export interface AgentUserInfoVO {
  loginId: string;
  realName: string;
  role: string;
  roles: string[];
  permissions: string[];
  extension?: string;
  endpointType?: string;
}

export const authApi = {
  sipConfig(): Promise<{ code: number; data: { extension: string; wsUrl: string; domain: string; password: string } }> {
    return adminApi.get('/auth/sip-config');
  },
  endpoints(): Promise<{ code: number; data: AgentEndpointsResp }> {
    return adminApi.get('/auth/endpoints');
  },
  switchEndpoint(data: { endpointType: 'WEBRTC' | 'SIP'; endpointValue?: string }): Promise<{ code: number; data: AgentEndpointsResp }> {
    return adminApi.put('/auth/endpoint', data);
  },
  login(data: AgentLoginReq): Promise<{ code: number; message: string; data: AgentLoginRespVO }> {
    return adminApi.post('/auth/login', data);
  },
  me(): Promise<{ code: number; message: string; data: AgentUserInfoVO }> {
    return adminApi.get('/auth/me');
  },
  logout(): Promise<{ code: number; message: string }> {
    return adminApi.post('/auth/logout');
  },
};
