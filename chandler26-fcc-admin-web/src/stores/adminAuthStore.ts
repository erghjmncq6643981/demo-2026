import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { authApi, type LoginReq, type UserInfoVO } from '../api/authApi';

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const token = ref<string | null>(localStorage.getItem('satoken'));
  const getInitialUser = (): UserInfoVO | null => {
    try {
      const raw = localStorage.getItem('fcc_admin_user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  };
  const user = ref<UserInfoVO | null>(getInitialUser());
  const loading = ref<boolean>(false);
  const error = ref<string | null>(null);

  const isAuthenticated = computed(() => !!token.value);
  const isAdmin = computed(() => user.value?.role === 'ADMIN');
  const roles = computed(() => user.value?.roles || (user.value?.role ? [user.value.role] : []));
  const permissions = computed(() => user.value?.permissions || []);

  async function login(req: LoginReq) {
    loading.value = true;
    error.value = null;
    try {
      const resp = await authApi.login(req);
      token.value = resp.tokenValue;
      localStorage.setItem('satoken', resp.tokenValue);
      
      const userInfo: UserInfoVO = {
        loginId: resp.loginId,
        realName: resp.realName,
        role: resp.role,
        roles: [resp.role],
        permissions: resp.permissions,
        extension: resp.extension,
        endpointType: resp.endpointType,
      };
      user.value = userInfo;
      localStorage.setItem('fcc_admin_user', JSON.stringify(userInfo));
      return resp;
    } catch (err: any) {
      error.value = err.message || '登录失败';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function fetchUser() {
    if (!token.value) return;
    try {
      const info = await authApi.getCurrentUser();
      user.value = info;
      localStorage.setItem('fcc_admin_user', JSON.stringify(info));
    } catch (err) {
      logout();
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await authApi.logout();
      }
    } catch (err) {
      // ignore
    } finally {
      token.value = null;
      user.value = null;
      localStorage.removeItem('satoken');
      localStorage.removeItem('fcc_admin_user');
    }
  }

  return {
    token,
    user,
    loading,
    error,
    isAuthenticated,
    isAdmin,
    roles,
    permissions,
    login,
    fetchUser,
    logout,
  };
});
