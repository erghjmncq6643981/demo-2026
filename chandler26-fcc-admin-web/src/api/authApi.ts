import apiClient from './apiClient';

export interface LoginReq {
  username: string;
  password: string;
  loginType?: string;
}

export interface LoginRespVO {
  tokenValue: string;
  tokenName: string;
  loginId: string;
  realName: string;
  role: string;
  permissions: string[];
  extension?: string;
  endpointType?: string;
}

export interface UserInfoVO {
  loginId: string;
  realName: string;
  role: string;
  roles: string[];
  permissions: string[];
  extension?: string;
  endpointType?: string;
}

export const authApi = {
  login(data: LoginReq): Promise<LoginRespVO> {
    return apiClient.post('/auth/login', data);
  },
  getCurrentUser(): Promise<UserInfoVO> {
    return apiClient.get('/auth/me');
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout');
  },
};
