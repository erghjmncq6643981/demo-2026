import axios from 'axios';

export const adminApi = axios.create({ baseURL: '/api/admin', timeout: 8000 });
export const telephonyApi = axios.create({ baseURL: '/api/telephony', timeout: 8000 });

// Both transports carry the authenticated identity. Never log Axios errors:
// their config includes authorization headers and sensitive response payloads.
for (const client of [adminApi, telephonyApi]) {
  client.interceptors.request.use(config => {
    const token = localStorage.getItem('fcc_agent_satoken');
    if (token) config.headers['satoken'] = token;
    return config;
  });
  client.interceptors.response.use(response => {
    if (response.data?.code !== 200) {
      return Promise.reject(new Error(response.data?.message || '业务操作失败'));
    }
    return response.data;
  }, error => Promise.reject(new Error(
    error.response?.data?.message || error.response?.data?.detail ||
    (error.response?.status === 504 ? '指令结果未知，请核对通话状态' : '请求失败，请检查登录和服务状态')
  )));
}
