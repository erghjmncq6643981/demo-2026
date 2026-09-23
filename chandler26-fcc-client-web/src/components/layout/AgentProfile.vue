<script setup lang="ts">
import EndpointSelector from '../../features/endpoint/components/EndpointSelector.vue';
import AgentPresenceControls from '../../features/identity/components/AgentPresenceControls.vue';
import CallStateBadge from '../../features/call/components/CallStateBadge.vue';
import { useAgentStore } from '../../stores/agentStore';

const agentStore = useAgentStore();
</script>

<template>
  <div class="px-4 sm:px-6 pt-3 pb-2 shrink-0">
    <div class="profile-card">
      <section class="identity" aria-label="当前坐席">
        <div class="avatar">{{ agentStore.agentName.substring(0, 1) || '坐' }}</div>
        <div>
          <div class="name-row">
            <strong>{{ agentStore.agentName || '未命名坐席' }}</strong>
            <span>工号 {{ agentStore.workNo || '-' }}</span>
          </div>
          <p v-if="agentStore.serviceGroup">{{ agentStore.serviceGroup }}</p>
        </div>
      </section>

      <div class="operating-state">
        <AgentPresenceControls />
        <CallStateBadge />
      </div>

      <EndpointSelector />
    </div>
  </div>
</template>

<style scoped>
.profile-card { display: grid; grid-template-columns: minmax(220px, 1fr) auto minmax(260px, 1fr); align-items: center; gap: 20px; border: 1px solid rgb(226 232 240 / 80%); border-radius: 16px; background: #fff; padding: 14px 16px; box-shadow: 0 1px 2px rgb(15 23 42 / 4%); }
.identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.avatar { display: grid; place-items: center; flex: 0 0 auto; width: 40px; height: 40px; border-radius: 12px; background: linear-gradient(145deg, #4f46e5, #4338ca); color: #fff; font-weight: 800; }
.name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.name-row strong { color: #0f172a; font-size: 16px; }
.name-row span { border: 1px solid #e2e8f0; border-radius: 6px; background: #f8fafc; padding: 2px 7px; color: #475569; font-family: monospace; font-size: 11px; font-weight: 700; }
.identity p { margin-top: 4px; color: #64748b; font-size: 11px; }
.operating-state { display: flex; align-items: end; gap: 14px; padding: 0 20px; border-right: 1px solid #e2e8f0; border-left: 1px solid #e2e8f0; }
@media (max-width: 1050px) {
  .profile-card { grid-template-columns: 1fr 1fr; }
  .operating-state { justify-content: flex-end; border-right: 0; border-left: 0; padding: 0; }
  .profile-card :deep(.endpoint-selector) { grid-column: 1 / -1; }
}
@media (max-width: 650px) {
  .profile-card { grid-template-columns: 1fr; }
  .operating-state { justify-content: space-between; }
  .profile-card :deep(.endpoint-selector) { grid-column: auto; }
}
</style>
