<script setup lang="ts">
import { computed } from 'vue';
import ExtensionListPage from '../features/extensions/pages/ExtensionListPage.vue';
import SystemConfigPage from '../features/settings/pages/SystemConfigPage.vue';
import ClientFleetPage from '../features/fleet/pages/ClientFleetPage.vue';

const props = withDefaults(defineProps<{
  initialTab?: 'extensions' | 'sysvars' | 'clients';
}>(), {
  initialTab: 'extensions',
});

const activePage = computed(() => props.initialTab);
</script>

<template>
  <ExtensionListPage v-if="activePage === 'extensions'" />
  <SystemConfigPage v-else-if="activePage === 'sysvars'" />
  <ClientFleetPage v-else />
</template>
