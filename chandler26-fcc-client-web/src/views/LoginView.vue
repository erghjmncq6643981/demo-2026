<script setup lang="ts">
import { ref } from 'vue';
import { useAgentStore } from '../stores/agentStore';
import { Headphones, Lock, User, AlertCircle, ArrowRight, Eye, EyeOff } from 'lucide-vue-next';

const agentStore = useAgentStore();

const workNo = ref('');
const password = ref('');
const showPassword = ref(false);
const submitting = ref(false);
const errorMsg = ref('');

const handleLogin = async () => {
  if (!workNo.value.trim()) {
    errorMsg.value = '请输入工号';
    return;
  }
  if (!password.value) {
    errorMsg.value = '请输入密码';
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

<template>
  <div class="w-screen h-screen flex items-center justify-center bg-slate-950 p-4 sm:p-6 lg:p-8 relative overflow-hidden font-sans">
    
    <!-- 背景微光氛围 -->
    <div class="absolute inset-0 pointer-events-none overflow-hidden">
      <div class="absolute -top-40 -left-40 w-[500px] h-[500px] bg-indigo-600/15 rounded-full blur-[120px]"></div>
      <div class="absolute -bottom-40 -right-40 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-[120px]"></div>
      <div class="absolute inset-0 bg-[radial-gradient(#312e81_1px,transparent_1px)] [background-size:24px_24px] opacity-20"></div>
    </div>

    <!-- 居中双栏卡片 -->
    <div class="relative z-10 w-full max-w-4xl min-h-[500px] bg-slate-900 rounded-3xl shadow-2xl shadow-black/80 border border-slate-800/80 overflow-hidden flex flex-col md:flex-row">
      
      <!-- 卡片左侧：品牌 / 系统信息 (极简深色) -->
      <div class="w-full md:w-1/2 p-8 sm:p-10 lg:p-12 flex flex-col justify-between bg-gradient-to-br from-slate-900 via-slate-900 to-indigo-950/80 border-b md:border-b-0 md:border-r border-slate-800/80 relative">
        
        <!-- 顶部 Logo 与系统名 -->
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-indigo-500/30">
            <Headphones class="w-5 h-5" />
          </div>
          <div>
            <div class="text-base font-bold tracking-tight text-white">FCC 智能呼叫中心</div>
            <div class="text-xs text-slate-400 font-medium">坐席工作台</div>
          </div>
        </div>

        <!-- 中部标语与要点 -->
        <div class="my-8">
          <h1 class="text-2xl sm:text-3xl font-bold tracking-tight text-white mb-3 leading-snug">
            坐席日常工作<br />
            从这里开始
          </h1>
          <p class="text-xs sm:text-sm text-slate-400 mb-6 leading-relaxed">
            登录后处理来电、人工外呼和回拨待办。请先完成话机绑定，再切换为就绪状态。
          </p>

          <div class="space-y-2.5 text-xs sm:text-sm text-slate-300">
            <div class="flex items-center gap-2.5">
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
              <span>来电提醒与授权客户摘要</span>
            </div>
            <div class="flex items-center gap-2.5">
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
              <span>实体话机绑定与坐席状态</span>
            </div>
            <div class="flex items-center gap-2.5">
              <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
              <span>人工外呼、回拨与话后处理</span>
            </div>
          </div>
        </div>

        <!-- 底部版本与内核状态 -->
        <div class="text-xs text-slate-500 flex items-center justify-between font-mono">
          <span>FCC 坐席工作台</span>
          <span>v2.0</span>
        </div>
      </div>

      <!-- 卡片右侧：高级质感登录框 -->
      <div class="w-full md:w-1/2 p-8 sm:p-10 lg:p-12 bg-white flex flex-col justify-center relative">
        
        <!-- 微妙的背景装饰 -->
        <div class="absolute top-0 right-0 w-32 h-32 bg-indigo-50/50 rounded-full blur-2xl pointer-events-none"></div>

        <div class="w-full max-w-sm mx-auto relative z-10">
          
          <!-- 标题区 (已彻底删除副标题说明) -->
          <div class="mb-7">
            <div class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-indigo-50 border border-indigo-100 text-[11px] font-bold text-indigo-600 mb-2 font-mono tracking-wider">
              AGENT PORTAL
            </div>
            <h2 class="text-2xl font-black text-slate-900 tracking-tight">坐席登录</h2>
          </div>

          <!-- 错误提示 -->
          <div
            v-if="errorMsg"
            class="mb-5 p-3 rounded-2xl bg-rose-50 border border-rose-200/80 text-rose-600 text-xs flex items-center gap-2 shadow-xs"
          >
            <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
            <span class="font-medium">{{ errorMsg }}</span>
          </div>

          <!-- 高质感登录表单 -->
          <form @submit.prevent="handleLogin" class="space-y-4">
            
            <!-- 工号输入项 -->
            <div>
              <label class="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                工号
              </label>
              <div class="group relative flex items-center bg-slate-50/80 hover:bg-slate-50 focus-within:bg-white rounded-2xl border border-slate-200 focus-within:border-indigo-500 focus-within:ring-4 focus-within:ring-indigo-500/10 shadow-[0_1px_3px_rgba(0,0,0,0.03)] transition-all duration-200 px-3 py-1">
                <div class="w-8 h-8 rounded-xl bg-white group-focus-within:bg-indigo-50 text-slate-400 group-focus-within:text-indigo-600 border border-slate-200/80 group-focus-within:border-indigo-200 shadow-2xs flex items-center justify-center shrink-0 transition-colors">
                  <User class="w-4 h-4" />
                </div>
                <input
                  v-model="workNo"
                  type="text"
                  required
                  placeholder="请输入坐席工号"
                  class="w-full bg-transparent border-0 px-3 py-2 text-sm font-semibold text-slate-900 placeholder:text-slate-400 placeholder:font-normal focus:outline-none"
                />
              </div>
            </div>

            <!-- 密码输入项 -->
            <div>
              <label class="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                密码
              </label>
              <div class="group relative flex items-center bg-slate-50/80 hover:bg-slate-50 focus-within:bg-white rounded-2xl border border-slate-200 focus-within:border-indigo-500 focus-within:ring-4 focus-within:ring-indigo-500/10 shadow-[0_1px_3px_rgba(0,0,0,0.03)] transition-all duration-200 px-3 py-1">
                <div class="w-8 h-8 rounded-xl bg-white group-focus-within:bg-indigo-50 text-slate-400 group-focus-within:text-indigo-600 border border-slate-200/80 group-focus-within:border-indigo-200 shadow-2xs flex items-center justify-center shrink-0 transition-colors">
                  <Lock class="w-4 h-4" />
                </div>
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  required
                  placeholder="请输入密码"
                  class="w-full bg-transparent border-0 px-3 py-2 text-sm font-semibold text-slate-900 placeholder:text-slate-400 placeholder:font-normal focus:outline-none"
                />
                <button
                  type="button"
                  @click="showPassword = !showPassword"
                  class="p-1.5 rounded-lg hover:bg-slate-200/60 text-slate-400 hover:text-slate-600 transition cursor-pointer"
                  tabindex="-1"
                >
                  <EyeOff v-if="showPassword" class="w-4 h-4" />
                  <Eye v-else class="w-4 h-4" />
                </button>
              </div>
            </div>

            <!-- 高级质感登录按钮 -->
            <div class="pt-3">
              <button
                type="submit"
                :disabled="submitting"
                class="relative w-full h-11 rounded-2xl bg-gradient-to-r from-indigo-600 via-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 active:scale-[0.99] text-white text-sm font-bold tracking-wide shadow-[0_8px_20px_-4px_rgba(79,70,229,0.35)] hover:shadow-[0_12px_24px_-4px_rgba(79,70,229,0.45)] transition-all duration-200 flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed overflow-hidden group"
              >
                <!-- 悬浮光效 -->
                <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-700 ease-out"></div>
                <span v-if="submitting">正在登录...</span>
                <template v-else>
                  <span>登 录</span>
                  <ArrowRight class="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
                </template>
              </button>
            </div>
          </form>

        </div>
      </div>

    </div>

  </div>
</template>
