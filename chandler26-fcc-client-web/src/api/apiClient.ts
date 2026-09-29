import axios from 'axios';

export function resolveBackendHost(): string {
  const custom = typeof localStorage !== 'undefined' ? localStorage.getItem('fcc_backend_host') : null;
  if (custom) return custom;
  if (typeof window !== 'undefined' && window.location?.hostname && !['localhost', '127.0.0.1', ''].includes(window.location.hostname)) {
    return window.location.hostname;
  }
  return 'fcc.local';
}

const initialHost = resolveBackendHost();
export const adminApi = axios.create({ baseURL: `http://${initialHost}:8089/api/admin`, timeout: 8000 });
export const telephonyApi = axios.create({ baseURL: `http://${initialHost}:8085/api/telephony`, timeout: 8000 });

function isUnauthorized(status?: number, data?: any): boolean {
  if (status === 401) return true;
  const code = data?.code ?? data?.status;
  if (code === 401 || (typeof code === 'number' && code >= 11011 && code <= 11016)) return true;
  const msg = typeof data?.message === 'string' ? data.message : '';
  return /登录已失效|请重新登录|请先登录|未能读取到有效\s*token|token\s*(无效|已过期|被顶下线|被踢下线)/i.test(msg);
}

function handleUnauthorized() {
  localStorage.removeItem('fcc_agent_satoken');
  localStorage.removeItem('fcc_agent_workno');
  localStorage.removeItem('fcc_agent_name');
  localStorage.removeItem('fcc_agent_role');
  localStorage.removeItem('fcc_agent_extension');
  localStorage.removeItem('fcc_agent_endpoint');
  localStorage.removeItem('fcc_webrtc_workno');
  localStorage.removeItem('fcc_bound_sip_extension');
  localStorage.removeItem('fcc_bound_mobile');
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('fcc:auth-expired'));
  }
}

// Both transports carry the authenticated identity. Never log Axios errors:
// their config includes authorization headers and sensitive response payloads.
for (const client of [adminApi, telephonyApi]) {
  client.interceptors.request.use(config => {
    const host = resolveBackendHost();
    if (config.baseURL?.includes('localhost') || config.baseURL?.includes('fcc.local')) {
      const port = config.baseURL.includes(':8089') ? 8089 : 8085;
      const path = config.baseURL.includes(':8089') ? '/api/admin' : '/api/telephony';
      config.baseURL = `http://${host}:${port}${path}`;
    }
    if (config.url?.endsWith('/auth/login')) {
      delete config.headers['satoken'];
      return config;
    }
    const token = localStorage.getItem('fcc_agent_satoken');
    if (token) config.headers['satoken'] = token;
    return config;
  });
  client.interceptors.response.use(
    response => {
      const data = response.data;
      if (isUnauthorized(response.status, data)) {
        handleUnauthorized();
        return Promise.reject(new Error(data?.message || '登录状态已失效，请重新登录'));
      }
      if (data?.code !== 200) {
        return Promise.reject(new Error(data?.message || '业务操作失败'));
      }
      return data;
    },
    error => {
      const status = error.response?.status;
      const data = error.response?.data;
      if (isUnauthorized(status, data)) {
        handleUnauthorized();
        return Promise.reject(new Error('登录已失效，请重新登录'));
      }
      return Promise.reject(new Error(
        data?.message || data?.detail ||
        (status === 504 ? '指令结果未知，请核对通话状态' : '请求失败，请检查登录和服务状态')
      ));
    }
  );
}
