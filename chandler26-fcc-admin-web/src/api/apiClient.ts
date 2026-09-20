import axios from 'axios';

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
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code !== 200) {
        return Promise.reject(new Error(data.message || '请求失败'));
      }
      return data.data;
    }
    return response.data;
  },
  (error) => {
    if (error.response && (error.response.status === 401 || (error.response.data && error.response.data.message?.includes('token')))) {
      localStorage.removeItem('satoken');
      localStorage.removeItem('fcc_admin_user');
      window.dispatchEvent(new CustomEvent('fcc-auth-unauthorized'));
    }
    const message = error.response?.data?.message || error.message || '网络连接异常';
    return Promise.reject(new Error(message));
  }
);

export default apiClient;
