<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-md p-4 animate-in fade-in duration-200">
    <div class="w-full max-w-md bg-white rounded-3xl shadow-2xl border border-slate-100 p-7 relative">
      <!-- 装饰背景 -->
      <div class="absolute -top-10 -right-10 w-32 h-32 bg-brand-500/10 rounded-full blur-2xl pointer-events-none"></div>

      <!-- 头部标识 -->
      <div class="flex items-center gap-3.5 mb-5">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-brand-600 via-indigo-600 to-purple-600 flex items-center justify-center text-white text-xl shadow-lg shadow-brand-500/25">
          🎧
        </div>
        <div>
          <h2 class="text-lg font-extrabold text-slate-900 tracking-tight flex items-center gap-2">
            坐席登录认证
            <span class="text-[10px] font-mono font-bold bg-brand-50 text-brand-700 px-2 py-0.5 rounded-full border border-brand-200">
              Sa-Token + FS
            </span>
          </h2>
          <p class="text-xs text-slate-400">呼叫中心坐席工作台 2.0 统一登录</p>
        </div>
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-xs flex items-center gap-2">
        <span>⚠️</span>
        <span>{{ errorMsg }}</span>
      </div>

      <!-- 登录表单 -->
      <form @submit.prevent="handleLogin" class="space-y-4 text-xs">
        <div>
          <label class="block font-bold text-slate-700 mb-1">坐席工号 (数字编号)</label>
          <input
            v-model="workNo"
            type="text"
            required
            placeholder="请输入坐席工号"
            class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 font-mono focus:outline-none focus:ring-2 focus:ring-brand-500 focus:bg-white transition"
          />
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1">登录密码</label>
          <input
            v-model="password"
            type="password"
            required
            placeholder="请输入密码"
            class="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 font-mono focus:outline-none focus:ring-2 focus:ring-brand-500 focus:bg-white transition"
          />
        </div>

        <div class="pt-2">
          <button
            type="submit"
            :disabled="submitting"
            class="w-full py-3 bg-gradient-to-r from-brand-600 via-indigo-600 to-purple-600 hover:from-brand-500 hover:to-indigo-500 text-white rounded-2xl font-extrabold text-sm shadow-lg shadow-brand-500/25 transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
          >
            <span v-if="submitting">正在进行 Sa-Token 鉴权...</span>
            <template v-else>
              <span>登 录 工 作 台</span>
              <span>➔</span>
            </template>
          </button>
        </div>
      </form>

      <div class="mt-5 pt-3 border-t border-slate-100 text-center">
        <p class="text-[11px] text-slate-400">
          登录后自动同步分配分机与 WebRTC 话机 · 账号由管理员在控制台创建
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useAgentStore } from '../../stores/agentStore';

const agentStore = useAgentStore();

const workNo = ref('');
const password = ref('');
const submitting = ref(false);
const errorMsg = ref('');

const handleLogin = async () => {
  if (!workNo.value.trim() || !password.value) {
    errorMsg.value = '请输入工号与密码';
    return;
  }
  submitting.value = true;
  errorMsg.value = '';
  try {
    await agentStore.login(workNo.value.trim(), password.value);
  } catch (err: any) {
    errorMsg.value = err.message || '登录失败，请检查工号与密码';
  } finally {
    submitting.value = false;
  }
};
</script>
