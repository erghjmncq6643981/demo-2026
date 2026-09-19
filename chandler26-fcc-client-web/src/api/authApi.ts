import { adminApi } from './apiClient';

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
