<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useAdminAuthStore } from '../stores/adminAuthStore';
import { callbackApi } from '../api/callbackApi';
import { healthApi, type SidecarHealthVO } from '../features/monitoring/api/healthApi';
import { toast, confirmAction } from '../utils/feedback';

defineProps<{
  activeTab: string;
}>();

const emit = defineEmits<{
  (e: 'update:activeTab', tab: string): void;
}>();

const authStore = useAdminAuthStore();
const showProbeModal = ref(false);
const probeResult = ref<SidecarHealthVO | null>(null);
const probeError = ref('');
const probing = ref(false);
const userName = computed(() => authStore.user?.realName || authStore.user?.loginId || '管理员');
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase());
const roleLabel = computed(() => authStore.user?.role || 'ADMIN');
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜间好';
  if (hour < 12) return '上午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});
const healthLabel = computed(() => {
  if (probing.value) return '探测中';
  if (!probeResult.value) return '状态未探测';
  if (probeResult.value.status === 'HEALTHY') return '节点健康';
  if (probeResult.value.status === 'DEGRADED') return '节点降级';
  return '节点不可用';
});

// 铃铛角标取自真实的未接待回拨待办数，不再使用写死的假红点
const pendingCallbackCount = ref(0);

const loadPendingCallbackCount = async () => {
  try {
    const res = await callbackApi.list({ status: 'PENDING', pageNum: 1, pageSize: 1 });
    pendingCallbackCount.value = res?.total ?? 0;
  } catch {
    pendingCallbackCount.value = 0;
  }
};

onMounted(() => {
  document.documentElement.style.fontSize = '';
  loadPendingCallbackCount();
  void probeHealth(false);
});

const handleLogout = async () => {
  const confirmed = await confirmAction('确认注销当前管理员登录会话吗？', {
    title: '退出登录',
    confirmText: '退出登录',
  });
  if (confirmed) {
    await authStore.logout();
  }
};

const probeHealth = async (openModal: boolean) => {
  if (openModal) showProbeModal.value = true;
  probing.value = true;
  probeError.value = '';
  try {
    probeResult.value = await healthApi.getSidecarHealth();
  } catch (error) {
    probeResult.value = null;
    probeError.value = (error as { message?: string })?.message || '健康探测请求失败';
  } finally {
    probing.value = false;
  }
};

const handleProbe = () => probeHealth(true);

/**
 * 提醒铃铛：只汇报数据库里真实存在的待办，不再拼造"全网接通率良好"之类的结论
 */
const handleNotify = () => {
  if (pendingCallbackCount.value > 0) {
    toast(`当前有 ${pendingCallbackCount.value} 条未接待回拨待处理，请前往「通话与回拨记录」跟进`, 'warning');
  } else {
    toast('暂无待处理的系统通知', 'info');
  }
};
</script>

<template>
  <!-- 🌟 全屏平铺工作台主容器 (100vw × 100vh 动态铺满整个屏幕，随分辨率自适应) -->
  <div class="w-screen h-screen bg-[#F8FAFD] flex overflow-hidden">
    
    <!-- 1. 左侧轻奢极简白底导航栏 (精简为 w-56，更紧凑高效) -->
    <aside class="w-56 bg-white border-r border-slate-100 flex flex-col justify-between p-4 shrink-0 z-20 h-full select-none">
      <div>
        <!-- 品牌 LOGO -->
        <div class="flex items-center gap-2.5 mb-5 px-1">
          <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-purple-600 flex items-center justify-center text-white text-lg shadow-md shadow-indigo-500/20 shrink-0">
            📦
          </div>
          <div>
            <div class="font-black text-base text-slate-900 tracking-tight leading-tight">箱箱通讯</div>
            <div class="text-[11px] font-semibold text-slate-400 mt-0.5">FCC Cloud 2.6</div>
          </div>
        </div>

        <!-- 业务导航菜单 -->
        <div class="space-y-4">
          <div>
            <div class="text-[11px] font-bold text-slate-400 uppercase tracking-wider px-2 mb-2">话务与监控</div>
            <div class="space-y-1 font-bold">
              
              <!-- 菜单 1: 通话与回拨记录 (整合通话记录与未接待回拨) -->
              <button
                @click="emit('update:activeTab', 'routes')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="(activeTab === 'routes' || activeTab === 'callback') ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="(activeTab === 'routes' || activeTab === 'callback') ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"/></svg>
                  <span>通话与回拨记录</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="(activeTab === 'routes' || activeTab === 'callback') ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 2: 客服组管理 -->
              <button
                @click="emit('update:activeTab', 'groups')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'groups' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'groups' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"/></svg>
                  <span>客服组管理</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'groups' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 3: 组织效能报表 (紧随客服组管理下方) -->
              <button
                @click="emit('update:activeTab', 'orgreport')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'orgreport' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'orgreport' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
                  <span>组织效能报表</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'orgreport' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 4: IVR 流程 (改名: IVR流程) -->
              <button
                @click="emit('update:activeTab', 'flows')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'flows' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'flows' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                  <span>IVR 流程</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'flows' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 5: 系统监控大盘 -->
              <button
                @click="emit('update:activeTab', 'running')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'running' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'running' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>
                  <span>系统监控大盘</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'running' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>
            </div>
          </div>

          <div>
            <div class="text-[11px] font-bold text-slate-400 uppercase tracking-wider px-2 mb-2">系统运维</div>
            <div class="space-y-1 font-bold">
              <!-- 菜单 6: 分机管理 -->
              <button
                @click="emit('update:activeTab', 'extensions')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'extensions' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'extensions' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"/></svg>
                  <span>分机管理</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'extensions' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 7: 系统变量 (业务配置) -->
              <button
                @click="emit('update:activeTab', 'sysvars')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'sysvars' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'sysvars' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"/></svg>
                  <span>系统变量 (业务配置)</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'sysvars' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>

              <!-- 菜单 8: 客户端管理 -->
              <button
                @click="emit('update:activeTab', 'clients')"
                class="w-full flex items-center justify-between px-3 py-2 rounded-xl transition-all cursor-pointer text-xs"
                :class="activeTab === 'clients' ? 'bg-brand-50 text-brand-600 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'"
              >
                <div class="flex items-center gap-2.5">
                  <svg class="w-4 h-4 shrink-0" :class="activeTab === 'clients' ? 'text-brand-500' : 'text-slate-400'" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
                  <span>客户端管理</span>
                </div>
                <span class="w-1.5 h-1.5 rounded-full shrink-0" :class="activeTab === 'clients' ? 'bg-brand-500' : 'bg-transparent'"></span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部中间件节点健康卡片 -->
      <div class="bg-gradient-to-br from-indigo-50 via-blue-50 to-purple-50 p-3 rounded-2xl border border-indigo-100/70 text-center relative overflow-hidden">
        <div class="w-7 h-7 rounded-full bg-white shadow-sm flex items-center justify-center text-xs font-bold text-brand-600 mx-auto mb-1.5">
          ⚡
        </div>
        <div class="text-xs font-bold text-slate-800 mb-0.5">通信中间件集群</div>
        <p class="text-[10px] text-slate-500 mb-2 leading-tight font-medium">FreeSWITCH + Sidecar</p>
        <button
          @click="handleProbe"
          class="w-full py-1.5 bg-white hover:bg-slate-50 text-brand-600 text-[11px] font-extrabold rounded-lg shadow-xs transition-all cursor-pointer"
        >
          实时性能探测
        </button>
      </div>
    </aside>

    <!-- 2. 右侧主工作区 (顶栏 + 模块内容渲染容器) -->
    <main class="flex-1 flex flex-col overflow-hidden bg-[#F8FAFD] h-full">

      <!-- 顶栏：欢迎问候 + 字体缩放控制器 + 搜索 + 管理员档案 -->
      <header class="px-8 pt-6 pb-4 flex items-center justify-between shrink-0">
        <div>
          <h1 class="text-2xl font-black text-slate-900 tracking-tight flex items-center gap-3">
            <span>{{ greeting }}，{{ userName }}！</span>
            <span
              class="text-xs font-bold px-3 py-1 rounded-full flex items-center gap-1.5 shadow-2xs"
              :class="probeResult?.status === 'HEALTHY' ? 'bg-emerald-100 text-emerald-800' : probeResult ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'"
            >
              <span class="w-2 h-2 rounded-full" :class="probeResult?.status === 'HEALTHY' ? 'bg-emerald-500' : probeResult ? 'bg-amber-500' : 'bg-slate-400'"></span>
              <span>{{ healthLabel }}</span>
            </span>
          </h1>
        </div>

        <!-- 顶栏右侧工具区 -->
        <div class="flex items-center gap-4">
          

          <!-- 提醒铃铛：角标为真实的未接待回拨待办数 -->
          <button
            @click="handleNotify"
            class="w-11 h-11 rounded-full bg-white border border-slate-200/80 hover:bg-slate-50 flex items-center justify-center text-slate-600 shadow-xs relative cursor-pointer"
            :title="pendingCallbackCount > 0 ? `${pendingCallbackCount} 条未接待回拨待处理` : '暂无待处理通知'"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>
            <span
              v-if="pendingCallbackCount > 0"
              class="absolute -top-1 -right-1 min-w-[1.15rem] h-[1.15rem] px-1 rounded-full bg-rose-500 text-white text-[10px] font-black flex items-center justify-center ring-2 ring-white"
            >
              {{ pendingCallbackCount > 99 ? '99+' : pendingCallbackCount }}
            </span>
          </button>

          <!-- 管理员头像与登出 -->
          <div class="flex items-center gap-3 pl-2">
            <div class="w-11 h-11 rounded-full bg-gradient-to-tr from-amber-400 via-orange-500 to-indigo-600 text-white font-extrabold flex items-center justify-center text-base shadow-sm ring-2 ring-white">
              {{ userInitial }}
            </div>
            <div class="hidden xl:block">
              <div class="text-sm font-bold text-slate-900 leading-tight">{{ userName }}</div>
              <div class="text-xs text-slate-400 font-medium">{{ roleLabel }}</div>
            </div>
            <button
              @click="handleLogout"
              class="ml-2 text-slate-400 hover:text-rose-600 transition cursor-pointer text-sm flex items-center gap-1"
              title="注销登录"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/></svg>
            </button>
          </div>
        </div>
      </header>

      <!-- 3. 工作区视图插槽 (铺满剩余视口空间) -->
      <div class="flex-1 px-8 pb-6 overflow-hidden flex flex-col h-full">
        <slot />
      </div>
    </main>

    <!-- 探活弹窗 -->
    <div v-if="showProbeModal" class="fixed inset-0 bg-slate-900/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-3xl p-6 max-w-xl w-full shadow-popover border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full" :class="probing ? 'bg-blue-500 animate-pulse' : probeResult?.status === 'HEALTHY' ? 'bg-emerald-500' : 'bg-amber-500'"></span>
            <span class="font-bold text-base text-slate-900">通信中间件集群健康报告</span>
          </div>
          <button @click="showProbeModal = false" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
        </div>
        <div v-if="probing" class="bg-slate-50 border border-slate-200 p-4 text-sm text-slate-600">正在通过管理端查询 Sidecar 与 FreeSWITCH 实时状态...</div>
        <div v-else-if="probeError" class="bg-rose-50 border border-rose-200 p-4 text-sm text-rose-700">{{ probeError }}</div>
        <dl v-else-if="probeResult" class="grid grid-cols-2 gap-x-5 gap-y-3 bg-slate-50 border border-slate-200 p-4 text-sm">
          <dt class="text-slate-500">综合状态</dt><dd class="font-semibold text-slate-900">{{ probeResult.status }}</dd>
          <dt class="text-slate-500">节点标识</dt><dd class="font-mono text-xs text-slate-900 break-all">{{ probeResult.nodeId || '-' }}</dd>
          <dt class="text-slate-500">节点状态</dt><dd class="font-semibold text-slate-900">{{ probeResult.nodeState || '-' }}</dd>
          <dt class="text-slate-500">FreeSWITCH</dt><dd :class="probeResult.freeSwitchAlive ? 'text-emerald-700' : 'text-rose-700'">{{ probeResult.freeSwitchAlive ? '可用' : '不可用' }}</dd>
          <dt class="text-slate-500">PostgreSQL</dt><dd :class="probeResult.databaseConnected ? 'text-emerald-700' : 'text-rose-700'">{{ probeResult.databaseConnected ? '可用' : '不可用' }}</dd>
          <dt class="text-slate-500">活跃通道</dt><dd class="font-mono text-slate-900">{{ probeResult.activeChannels ?? '-' }} / {{ probeResult.maxChannels ?? '-' }}</dd>
          <dt class="text-slate-500">探测时间</dt><dd class="text-slate-900">{{ probeResult.checkedAt }}</dd>
        </dl>
        <div class="flex justify-end">
          <button @click="showProbeModal = false" class="px-6 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition cursor-pointer">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
