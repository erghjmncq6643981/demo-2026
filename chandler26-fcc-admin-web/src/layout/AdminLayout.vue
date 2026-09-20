<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { Menu, Server } from "lucide-vue-next";
import AdminSidebar from "./AdminSidebar.vue";
import { useAdminAuthStore } from "../stores/adminAuthStore";
import { callbackApi } from "../api/callbackApi";
import {
  healthApi,
  type SidecarHealthVO,
} from "../features/monitoring/api/healthApi";
import { toast, confirmAction } from "../utils/feedback";

defineProps<{
  activeTab: string;
}>();

const emit = defineEmits<{
  (e: "update:activeTab", tab: string): void;
}>();

const authStore = useAdminAuthStore();
const mobileNavOpen = ref(false);
const showProbeModal = ref(false);
const probeResult = ref<SidecarHealthVO | null>(null);
const probeError = ref("");
const probing = ref(false);
const userName = computed(
  () => authStore.user?.realName || authStore.user?.loginId || "管理员",
);
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase());
const roleLabel = computed(() => authStore.user?.role || "ADMIN");
const canManageBusiness = computed(() => {
  const permissions = authStore.permissions;
  return permissions.includes("*") || permissions.includes("business:manage");
});
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return "夜间好";
  if (hour < 12) return "上午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});
const healthLabel = computed(() => {
  if (probing.value) return "探测中";
  if (!probeResult.value) return "状态未探测";
  if (probeResult.value.status === "HEALTHY") return "节点健康";
  if (probeResult.value.status === "DEGRADED") return "节点降级";
  return "节点不可用";
});

// 铃铛角标取自真实的未接待回拨待办数，不再使用写死的假红点
const pendingCallbackCount = ref(0);

const loadPendingCallbackCount = async () => {
  try {
    const res = await callbackApi.list({
      status: "PENDING",
      pageNum: 1,
      pageSize: 1,
    });
    pendingCallbackCount.value = res?.total ?? 0;
  } catch {
    pendingCallbackCount.value = 0;
  }
};

onMounted(() => {
  document.documentElement.style.fontSize = "";
  loadPendingCallbackCount();
  void probeHealth(false);
});

const handleLogout = async () => {
  const confirmed = await confirmAction("确认注销当前管理员登录会话吗？", {
    title: "退出登录",
    confirmText: "退出登录",
  });
  if (confirmed) {
    await authStore.logout();
  }
};

const probeHealth = async (openModal: boolean) => {
  if (openModal) showProbeModal.value = true;
  probing.value = true;
  probeError.value = "";
  try {
    probeResult.value = await healthApi.getSidecarHealth();
  } catch (error) {
    probeResult.value = null;
    probeError.value =
      (error as { message?: string })?.message || "健康探测请求失败";
  } finally {
    probing.value = false;
  }
};

const handleProbe = () => probeHealth(true);

const selectTab = (tab: string) => {
  emit("update:activeTab", tab);
  mobileNavOpen.value = false;
};

/**
 * 提醒铃铛：只汇报数据库里真实存在的待办，不再拼造"全网接通率良好"之类的结论
 */
const handleNotify = () => {
  if (pendingCallbackCount.value > 0) {
    toast(
      `当前有 ${pendingCallbackCount.value} 条未接待回拨待处理，请前往「通话与回拨记录」跟进`,
      "warning",
    );
  } else {
    toast("暂无待处理的系统通知", "info");
  }
};
</script>

<template>
  <!-- 🌟 全屏平铺工作台主容器 (100vw × 100vh 动态铺满整个屏幕，随分辨率自适应) -->
  <div class="w-screen h-screen bg-[#F8FAFD] flex overflow-hidden">
    <div class="hidden lg:block h-full shrink-0">
      <AdminSidebar
        :active-tab="activeTab"
        :can-manage-business="canManageBusiness"
        @select="selectTab"
      />
    </div>

    <el-drawer
      v-model="mobileNavOpen"
      class="mobile-nav-drawer"
      direction="ltr"
      size="240px"
      :with-header="false"
      append-to-body
    >
      <AdminSidebar
        :active-tab="activeTab"
        :can-manage-business="canManageBusiness"
        @select="selectTab"
      />
    </el-drawer>

    <!-- 2. 右侧主工作区 (顶栏 + 模块内容渲染容器) -->
    <main
      class="flex-1 min-w-0 flex flex-col overflow-hidden bg-[#F8FAFD] h-full"
    >
      <!-- 顶栏：欢迎问候 + 字体缩放控制器 + 搜索 + 管理员档案 -->
      <header
        class="px-3 sm:px-6 lg:px-8 pt-4 lg:pt-6 pb-4 flex items-center justify-between gap-3 shrink-0"
      >
        <div class="flex items-center gap-3 min-w-0">
          <button
            type="button"
            class="lg:hidden w-10 h-10 shrink-0 rounded-lg bg-white border border-slate-200 hover:bg-slate-50 flex items-center justify-center text-slate-600"
            title="打开导航菜单"
            aria-label="打开导航菜单"
            @click="mobileNavOpen = true"
          >
            <Menu class="w-5 h-5" />
          </button>
          <h1
            class="min-w-0 text-xl font-black text-slate-900 tracking-tight flex items-center gap-3"
          >
            <span class="truncate">{{ greeting }}，{{ userName }}！</span>
            <span
              class="hidden md:flex text-xs font-bold px-3 py-1 rounded-full items-center gap-1.5 shadow-2xs shrink-0"
              :class="
                probeResult?.status === 'HEALTHY'
                  ? 'bg-emerald-100 text-emerald-800'
                  : probeResult
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-slate-100 text-slate-600'
              "
            >
              <span
                class="w-2 h-2 rounded-full"
                :class="
                  probeResult?.status === 'HEALTHY'
                    ? 'bg-emerald-500'
                    : probeResult
                      ? 'bg-amber-500'
                      : 'bg-slate-400'
                "
              ></span>
              <span>{{ healthLabel }}</span>
            </span>
          </h1>
        </div>

        <!-- 顶栏右侧工具区 -->
        <div class="flex items-center gap-2 sm:gap-4 shrink-0">
          <button
            @click="handleProbe"
            class="w-11 h-11 shrink-0 rounded-full bg-white border border-slate-200 hover:bg-slate-50 flex items-center justify-center text-slate-600"
            :title="`通信中间件集群：${healthLabel}`"
            aria-label="查看通信中间件集群"
            aria-haspopup="dialog"
            :aria-expanded="showProbeModal"
          >
            <Server class="w-5 h-5" aria-hidden="true" />
          </button>

          <!-- 提醒铃铛：角标为真实的未接待回拨待办数 -->
          <button
            @click="handleNotify"
            class="w-11 h-11 rounded-full bg-white border border-slate-200/80 hover:bg-slate-50 flex items-center justify-center text-slate-600 shadow-xs relative cursor-pointer"
            :title="
              pendingCallbackCount > 0
                ? `${pendingCallbackCount} 条未接待回拨待处理`
                : '暂无待处理通知'
            "
          >
            <svg
              class="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
              />
            </svg>
            <span
              v-if="pendingCallbackCount > 0"
              class="absolute -top-1 -right-1 min-w-[1.15rem] h-[1.15rem] px-1 rounded-full bg-rose-500 text-white text-[10px] font-black flex items-center justify-center ring-2 ring-white"
            >
              {{ pendingCallbackCount > 99 ? "99+" : pendingCallbackCount }}
            </span>
          </button>

          <!-- 管理员头像与登出 -->
          <div class="flex items-center gap-2 sm:gap-3 sm:pl-2">
            <div
              class="hidden sm:flex w-11 h-11 rounded-full bg-gradient-to-tr from-amber-400 via-orange-500 to-indigo-600 text-white font-extrabold items-center justify-center text-base shadow-sm ring-2 ring-white"
            >
              {{ userInitial }}
            </div>
            <div class="hidden xl:block">
              <div class="text-sm font-bold text-slate-900 leading-tight">
                {{ userName }}
              </div>
              <div class="text-xs text-slate-400 font-medium">
                {{ roleLabel }}
              </div>
            </div>
            <button
              @click="handleLogout"
              class="sm:ml-2 w-10 h-10 text-slate-400 hover:text-rose-600 transition cursor-pointer text-sm flex items-center justify-center"
              title="注销登录"
            >
              <svg
                class="w-5 h-5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                />
              </svg>
            </button>
          </div>
        </div>
      </header>

      <!-- 3. 工作区视图插槽 (铺满剩余视口空间) -->
      <div
        class="flex-1 px-3 sm:px-6 lg:px-8 pb-3 lg:pb-6 overflow-hidden flex flex-col h-full"
      >
        <slot />
      </div>
    </main>

    <!-- 探活弹窗 -->
    <el-dialog
      v-model="showProbeModal"
      title="通信中间件集群"
      width="min(36rem, calc(100vw - 2rem))"
      append-to-body
      :z-index="3000"
    >
      <div
        class="bg-white rounded-3xl p-6 max-w-xl w-full shadow-popover border border-slate-100 space-y-4"
      >
        <div
          class="flex justify-between items-center pb-3 border-b border-slate-100"
        >
          <div class="flex items-center gap-2">
            <span
              class="w-2.5 h-2.5 rounded-full"
              :class="
                probing
                  ? 'bg-blue-500 animate-pulse'
                  : probeResult?.status === 'HEALTHY'
                    ? 'bg-emerald-500'
                    : 'bg-amber-500'
              "
            ></span>
            <span class="font-bold text-base text-slate-900"
              >通信中间件集群健康报告</span
            >
          </div>
          <button
            @click="showProbeModal = false"
            class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer"
          >
            ✕
          </button>
        </div>
        <div
          v-if="probing"
          class="bg-slate-50 border border-slate-200 p-4 text-sm text-slate-600"
        >
          正在通过管理端查询 Sidecar 与 FreeSWITCH 实时状态...
        </div>
        <div
          v-else-if="probeError"
          class="bg-rose-50 border border-rose-200 p-4 text-sm text-rose-700"
        >
          {{ probeError }}
        </div>
        <dl
          v-else-if="probeResult"
          class="grid grid-cols-2 gap-x-5 gap-y-3 bg-slate-50 border border-slate-200 p-4 text-sm"
        >
          <dt class="text-slate-500">综合状态</dt>
          <dd class="font-semibold text-slate-900">{{ probeResult.status }}</dd>
          <dt class="text-slate-500">节点标识</dt>
          <dd class="font-mono text-xs text-slate-900 break-all">
            {{ probeResult.nodeId || "-" }}
          </dd>
          <dt class="text-slate-500">节点状态</dt>
          <dd class="font-semibold text-slate-900">
            {{ probeResult.nodeState || "-" }}
          </dd>
          <dt class="text-slate-500">FreeSWITCH</dt>
          <dd
            :class="
              probeResult.freeSwitchAlive ? 'text-emerald-700' : 'text-rose-700'
            "
          >
            {{ probeResult.freeSwitchAlive ? "可用" : "不可用" }}
          </dd>
          <dt class="text-slate-500">PostgreSQL</dt>
          <dd
            :class="
              probeResult.databaseConnected
                ? 'text-emerald-700'
                : 'text-rose-700'
            "
          >
            {{ probeResult.databaseConnected ? "可用" : "不可用" }}
          </dd>
          <dt class="text-slate-500">活跃通道</dt>
          <dd class="font-mono text-slate-900">
            {{ probeResult.activeChannels ?? "-" }} /
            {{ probeResult.maxChannels ?? "-" }}
          </dd>
          <dt class="text-slate-500">探测时间</dt>
          <dd class="text-slate-900">{{ probeResult.checkedAt }}</dd>
        </dl>
        <div class="flex justify-end">
          <el-button :loading="probing" @click="handleProbe"
            >重新探测</el-button
          >
          <button
            @click="showProbeModal = false"
            class="px-6 py-2 bg-brand-500 hover:bg-brand-600 text-white rounded-xl text-xs font-bold shadow-xs transition cursor-pointer"
          >
            关闭
          </button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.mobile-nav-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
</style>
