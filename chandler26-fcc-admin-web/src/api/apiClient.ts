import axios from 'axios';
import { toastWarning } from '../utils/feedback';

const apiClient = axios.create({
  baseURL: '/api/admin',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  transformResponse: [
    (data) => {
      if (typeof data === 'string') {
        // 防止雪花算法等 16 位以上的大整数在 JSON.parse 时丢失低位精度
        const safeData = data.replace(/([\[:])\s*(\d{16,})\s*([,\}\]])/g, '$1"$2"$3');
        try {
          return JSON.parse(safeData);
        } catch {
          return data;
        }
      }
      return data;
    },
  ],
});

let isHandlingUnauthorized = false;

/**
 * 统一处理登录会话失效：清理本地凭据、派发全局状态清理事件并单飞防抖提示
 */
export function handleUnauthorizedSession(message = '登录状态已失效，请重新登录'): void {
  // 1. 立即清理本地持久化凭据
  localStorage.removeItem('satoken');
  localStorage.removeItem('fcc_admin_user');

  // 2. 派发全局事件通知 Pinia AuthStore 及根组件完成响应式状态清空与视图切回
  window.dispatchEvent(
    new CustomEvent('fcc-auth-unauthorized', {
      detail: { message },
    })
  );

  // 3. 防抖提示：并发多个接口同时返回 401 时仅弹出一次 Toast
  if (!isHandlingUnauthorized) {
    isHandlingUnauthorized = true;
    toastWarning(message);
    setTimeout(() => {
      isHandlingUnauthorized = false;
    }, 1500);
  }
}

/**
 * 判定接口响应是否为登录凭据失效/未登录
 */
function isUnauthorizedResponse(status?: number, data?: any, url?: string): boolean {
  // 登录和登出接口自身不触发会话过期重定向逻辑
  if (url && (url.includes('/auth/login') || url.includes('/auth/logout'))) {
    return false;
  }

  // 1. 标准 HTTP 401 Unauthorized
  if (status === 401) {
    return true;
  }

  // 2. 业务状态码 401 或 Sa-Token 专属未登录异常码 (11011~11016)
  const code = data?.code ?? data?.status;
  if (code === 401 || (typeof code === 'number' && code >= 11011 && code <= 11016)) {
    return true;
  }

  // 3. 错误文案中包含凭据失效、未登录、请重新登录等典型模式
  const msg = typeof data?.message === 'string' ? data.message : '';
  if (/登录已失效|请重新登录|请先登录|未能读取到有效\s*token|token\s*(无效|已过期|被顶下线|被踢下线)/i.test(msg)) {
    return true;
  }

  return false;
}

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('satoken');
    if (token) {
      config.headers['satoken'] = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => {
    const data = response.data;
    const url = response.config?.url;

    // 检查 HTTP 200 状态下业务包裹体是否标识为未登录/凭据失效
    if (data && typeof data === 'object' && isUnauthorizedResponse(response.status, data, url)) {
      const msg = data.message || '登录状态已失效，请重新登录';
      handleUnauthorizedSession(msg);
      return Promise.reject(new Error(msg));
    }

    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code !== 200) {
        return Promise.reject(new Error(data.message || '请求失败'));
      }
      return data.data;
    }
    return response.data;
  },
  (error) => {
    const status = error.response?.status;
    const data = error.response?.data;
    const url = error.config?.url;

    if (isUnauthorizedResponse(status, data, url)) {
      const msg = data?.message || '登录状态已失效，请重新登录';
      handleUnauthorizedSession(msg);
      return Promise.reject(new Error(msg));
    }

    const message = error.response?.data?.message || error.message || '网络连接异常';
    return Promise.reject(new Error(message));
  }
);

export default apiClient;
