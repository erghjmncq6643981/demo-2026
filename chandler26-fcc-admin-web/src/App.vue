<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from "vue";
import { useAdminAuthStore } from "./stores/adminAuthStore";
import LoginView from "./views/LoginView.vue";
import AdminLayout from "./layout/AdminLayout.vue";
const OrgReportView = defineAsyncComponent(
  () => import("./views/OrgReportView.vue"),
);
const CdrReportView = defineAsyncComponent(
  () => import("./views/CdrReportView.vue"),
);
const GroupManageView = defineAsyncComponent(
  () => import("./views/GroupManageView.vue"),
);
const AgentManageView = defineAsyncComponent(
  () => import("./views/AgentManageView.vue"),
);
const IvrFlowView = defineAsyncComponent(
  () => import("./views/IvrFlowView.vue"),
);
const MonitorView = defineAsyncComponent(
  () => import("./views/MonitorView.vue"),
);
const ExtensionManageView = defineAsyncComponent(
  () => import("./views/ExtensionManageView.vue"),
);
const CustomerManagementPage = defineAsyncComponent(
  () => import("./features/customers/pages/CustomerManagementPage.vue"),
);
const DialJobManagementPage = defineAsyncComponent(
  () => import("./features/outbound/pages/DialJobManagementPage.vue"),
);

const authStore = useAdminAuthStore();
// 默认进入首页：通话与回拨记录
const activeTab = ref("routes");
const canManageBusiness = computed(() => {
  const permissions = authStore.permissions;
  return permissions.includes("*") || permissions.includes("business:manage");
});

watch(canManageBusiness, (allowed) => {
  if (!allowed && ["customers", "dial-jobs"].includes(activeTab.value)) {
    activeTab.value = "routes";
  }
});

const handleUnauthorized = () => {
  authStore.clearAuth();
  activeTab.value = "routes";
};

onMounted(() => {
  window.addEventListener("fcc-auth-unauthorized", handleUnauthorized);
  if (authStore.isAuthenticated) {
    authStore.fetchUser();
  }
});

onUnmounted(() => {
  window.removeEventListener("fcc-auth-unauthorized", handleUnauthorized);
});
</script>

<template>
  <div class="w-full h-full font-sans">
    <!-- 未认证：展示登录界面 -->
    <LoginView v-if="!authStore.isAuthenticated" />

    <!-- 已认证：展示管理主控制台 -->
    <AdminLayout v-else v-model:activeTab="activeTab">
      <!-- 菜单 1: 通话与回拨记录 (整合通话记录与未接待回拨两大 Tab) -->
      <CdrReportView
        v-if="activeTab === 'routes' || activeTab === 'callback'"
        :initialTab="activeTab === 'callback' ? 'callback' : 'records'"
      />

      <CustomerManagementPage
        v-else-if="activeTab === 'customers' && canManageBusiness"
      />

      <DialJobManagementPage
        v-else-if="activeTab === 'dial-jobs' && canManageBusiness"
      />

      <!-- 菜单 2: 客服组与排队 (module-groups) -->
      <GroupManageView v-else-if="activeTab === 'groups'" />

      <!-- 菜单 2.1: 坐席人员 -->
      <AgentManageView v-else-if="activeTab === 'agents'" />

      <!-- 菜单 3: IVR 流程编排 (module-flows) -->
      <IvrFlowView v-else-if="activeTab === 'flows'" />

      <!-- 菜单 4: 系统监控大盘 (module-running) -->
      <MonitorView v-else-if="activeTab === 'running'" />

      <!-- 菜单 5: 组织效能报表 -->
      <OrgReportView v-else-if="activeTab === 'orgreport'" />

      <!-- 菜单 6/7/8: 系统运维 (分机管理 / 系统变量 / 客户端管理) -->
      <ExtensionManageView
        v-else-if="
          activeTab === 'extensions' ||
          activeTab === 'sysvars' ||
          activeTab === 'clients'
        "
        :initialTab="
          activeTab === 'sysvars'
            ? 'sysvars'
            : activeTab === 'clients'
              ? 'clients'
              : 'extensions'
        "
      />
    </AdminLayout>
  </div>
</template>
