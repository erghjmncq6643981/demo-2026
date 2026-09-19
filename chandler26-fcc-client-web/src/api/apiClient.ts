import axios from 'axios';

export const adminApi = axios.create({
  baseURL: '/api/admin',
  timeout: 8000,
});

adminApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('fcc_agent_satoken');
    if (token) {
      config.headers['satoken'] = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const telephonyApi = axios.create({
  baseURL: '/api/telephony',
  timeout: 8000,
});

adminApi.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('Admin API error:', error);
    return Promise.reject(error);
  }
);

telephonyApi.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('Telephony API error:', error);
    return Promise.reject(error);
  }
);
