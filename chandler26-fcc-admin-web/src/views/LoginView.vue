<script setup lang="ts">
import { ref } from 'vue';
import { useAdminAuthStore } from '../stores/adminAuthStore';
import { Shield, Lock, User, AlertCircle, ArrowRight } from 'lucide-vue-next';

const authStore = useAdminAuthStore();

const username = ref('');
const password = ref('');
const errorMessage = ref('');

const handleLogin = async () => {
  if (!username.value || !password.value) {
    errorMessage.value = '请输入管理员账号与密码';
    return;
  }
  errorMessage.value = '';
  try {
    await authStore.login({
      username: username.value,
      password: password.value,
      loginType: 'ADMIN',
    });
  } catch (err: any) {
    errorMessage.value = err.message || '登录验证失败，请检查账号密码';
  }
};
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 px-4">
    <!-- Ambient Background Glow -->
    <div class="absolute inset-0 overflow-hidden pointer-events-none">
      <div class="absolute -top-40 -left-40 w-96 h-96 bg-indigo-600/20 rounded-full blur-3xl"></div>
      <div class="absolute -bottom-40 -right-40 w-96 h-96 bg-blue-600/20 rounded-full blur-3xl"></div>
    </div>

    <div class="relative w-full max-w-md bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-3xl p-8 shadow-2xl shadow-black/60">
      <!-- Header -->
      <div class="flex items-center space-x-3 mb-8">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-blue-500 flex items-center justify-center shadow-lg shadow-indigo-500/30">
          <Shield class="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 class="text-xl font-bold tracking-tight text-white flex items-center gap-2">
            FCC 管理控制中心
            <span class="text-xs font-mono font-normal bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 px-2 py-0.5 rounded-full">v2.0</span>
          </h1>
          <p class="text-xs text-slate-400">FreeSWITCH Call Center Operations Console</p>
        </div>
      </div>

      <!-- Error Alert -->
      <div v-if="errorMessage" class="mb-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-center space-x-2 text-xs text-rose-400">
        <AlertCircle class="w-4 h-4 flex-shrink-0" />
        <span>{{ errorMessage }}</span>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleLogin" class="space-y-4">
        <div>
          <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">管理员账号</label>
          <div class="relative">
            <User class="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
            <input
              v-model="username"
              type="text"
              required
              placeholder="请输入管理员账号"
              class="w-full pl-10 pr-4 py-2.5 bg-slate-800/80 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
            />
          </div>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">认证密码</label>
          <div class="relative">
            <Lock class="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
            <input
              v-model="password"
              type="password"
              required
              placeholder="请输入密码"
              class="w-full pl-10 pr-4 py-2.5 bg-slate-800/80 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
            />
          </div>
        </div>

        <button
          type="submit"
          :disabled="authStore.loading"
          class="w-full mt-4 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-blue-600 hover:from-indigo-500 hover:to-blue-500 text-white text-sm font-semibold shadow-lg shadow-indigo-500/25 flex items-center justify-center space-x-2 transition-all disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        >
          <span v-if="authStore.loading">正在安全鉴权...</span>
          <template v-else>
            <span>登 录 控 制 台</span>
            <ArrowRight class="w-4 h-4" />
          </template>
        </button>
      </form>
    </div>
  </div>
</template>
