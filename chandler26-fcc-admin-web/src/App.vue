<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useAdminAuthStore } from './stores/adminAuthStore';
import LoginView from './views/LoginView.vue';
import AdminLayout from './layout/AdminLayout.vue';
import OrgReportView from './views/OrgReportView.vue';
import CdrReportView from './views/CdrReportView.vue';
import GroupManageView from './views/GroupManageView.vue';
import IvrFlowView from './views/IvrFlowView.vue';
import MonitorView from './views/MonitorView.vue';
import CallbackView from './views/CallbackView.vue';
import ExtensionManageView from './views/ExtensionManageView.vue';

const authStore = useAdminAuthStore();
// 默认进入首页：通话与回拨记录
const activeTab = ref('routes');

const handleUnauthorized = () => {
  authStore.logout();
};

onMounted(() => {
  window.addEventListener('fcc-auth-unauthorized', handleUnauthorized);
  if (authStore.isAuthenticated) {
    authStore.fetchUser();
  }
});

onUnmounted(() => {
  window.removeEventListener('fcc-auth-unauthorized', handleUnauthorized);
});
</script>

<template>
  <div class="w-full h-full font-sans">
    <!-- 未认证：展示登录界面 -->
    <LoginView v-if="!authStore.isAuthenticated" />

    <!-- 已认证：展示 100% 还原原型的管理主控制台 -->
    <AdminLayout v-else v-model:activeTab="activeTab">
      <!-- 菜单 1: 通话与回拨记录 (整合通话记录与未接待回拨两大 Tab) -->
      <CdrReportView v-if="activeTab === 'routes' || activeTab === 'callback'" :initialTab="activeTab === 'callback' ? 'callback' : 'records'" />

      <!-- 菜单 2: 客服组与排队 (module-groups) -->
      <GroupManageView v-else-if="activeTab === 'groups'" />

      <!-- 菜单 3: IVR 流程编排 (module-flows) -->
      <IvrFlowView v-else-if="activeTab === 'flows'" />

      <!-- 菜单 4: 系统监控大盘 (module-running) -->
      <MonitorView v-else-if="activeTab === 'running'" />

      <!-- 菜单 5: 组织效能报表 (module-orgreport, 对齐截图 media_1789724474957.png) -->
      <OrgReportView v-else-if="activeTab === 'orgreport'" />

      <!-- 菜单 6/7/8: 系统运维 (分机管理 / 系统变量 / 客户端管理) -->
      <ExtensionManageView
        v-else-if="activeTab === 'extensions' || activeTab === 'sysvars' || activeTab === 'clients'"
        :initialTab="activeTab === 'sysvars' ? 'sysvars' : (activeTab === 'clients' ? 'clients' : 'extensions')"
        @update:activeTab="activeTab = $event"
      />
    </AdminLayout>
  </div>
</template>
