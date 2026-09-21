<template>
  <!-- 全局反馈层宿主 (Toast / Confirm)，替代浏览器原生弹窗 -->
  <FeedbackHost />

  <!-- 1. 未登录状态: 呈现全新企业级全屏双栏登录页 (左侧品牌/系统信息，右侧极简登录卡片) -->
  <LoginView v-if="!agentStore.isLoggedIn" />

  <!-- 2. 已登录状态: 呈现完整 PC 坐席工作台 -->
  <div v-else class="w-screen h-screen bg-[#F8FAFD] flex flex-col overflow-hidden relative font-sans">
    <!-- 窗口极简顶栏 (含软电话显隐开关、来电测试) -->
    <HeaderBar
      @open-ws-diagnostics="showWsModal = true"
      @toggle-softphone="handleToggleSoftphone"
    />

    <!-- 坐席接听方式与终端状态 -->
    <AgentProfile />

    <!-- 
      核心内容与业务工作台流转:
      - 若处于接通通话中 (CONNECTED): 全屏切入【InCallWorkspace 通话中工作台】，呈现通话信息与运单/订单快速查询
      - 否则处于正常状态: 默认呈现首屏最高优先级的【未接待回拨 Table】或【通话话单】
    -->
    <main class="flex-1 px-4 sm:px-6 pb-4 pt-1 overflow-y-auto flex flex-col min-h-0">
      
      <!-- 通话中沉浸式业务查询工作台 (通话中触发，挂断后自动平滑切回) -->
      <InCallWorkspace v-if="callStore.callState === 'CONNECTED' || callStore.callState === 'ENDING'" />

      <!-- 待命态: 业务 Tab 切换与智能外呼栏 + 数据表格 -->
      <div v-else class="flex-1 flex flex-col min-h-0">
        <!-- 业务 Tab 与外呼栏 (未接待回拨排在第一位) -->
        <OutboundBar
          ref="outboundBarRef"
          v-model:current-tab="activeTab"
        />

        <!-- Tab 1: 未接待回拨待办 Table (第一优先级，首屏默认) -->
        <CallbackQueue
          v-if="activeTab === 'callback'"
        />

        <!-- Tab 2: 真实 MySQL 通话话单记录 (严格按需求 8 列顺序) -->
        <CallRecordsTable
          v-else-if="activeTab === 'records'"
          @outbound="handleTriggerOutbound"
        />

        <!-- Tab 3: 坐席实时监控 -->
        <AgentMonitorView
          v-else-if="activeTab === 'agents'"
          @view-agent-records="handleViewAgentRecords"
        />
      </div>
    </main>

    <!-- 底部极简状态栏 -->
    <StatusBar />

    <!-- ================= 核心呼叫组件与弹屏 ================= -->
    <!-- 话机/手机接听模式下的居中声波放射响铃弹屏 -->
    <IncomingCallCard />
    <AcwDrawer />

    <!-- 高颜值现代软电话拨号盘 (仅当接听方式为软话机 WebRTC 时加载) -->
    <SoftphoneDialer v-if="agentStore.endpoint === 'WEBRTC'" ref="softphoneDialerRef" />

    <!-- WebSocket 信道诊断模态框 -->
    <WsDiagnosticsModal
      :visible="showWsModal"
      @close="showWsModal = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';
import HeaderBar from './components/layout/HeaderBar.vue';
import AgentProfile from './components/layout/AgentProfile.vue';
import StatusBar from './components/layout/StatusBar.vue';
import IncomingCallCard from './components/telephony/IncomingCallCard.vue';
import AcwDrawer from './components/telephony/AcwDrawer.vue';
import InCallWorkspace from './components/telephony/InCallWorkspace.vue';
import SoftphoneDialer from './components/telephony/SoftphoneDialer.vue';
import WsDiagnosticsModal from './components/telephony/WsDiagnosticsModal.vue';
import CallRecordsTable from './components/records/CallRecordsTable.vue';
import OutboundBar from './components/records/OutboundBar.vue';
import CallbackQueue from './components/records/CallbackQueue.vue';
import AgentMonitorView from './components/agents/AgentMonitorView.vue';
import LoginView from './views/LoginView.vue';
import FeedbackHost from './components/layout/FeedbackHost.vue';

import { useAgentStore } from './stores/agentStore';
import { useCallStore } from './stores/callStore';
import { useCdrStore } from './stores/cdrStore';
import { wsService } from './services/websocketService';
import { sipWebRtcService } from './services/sipWebRtcService';
import { getRuntimeConfig } from './shared/config/runtimeConfig';
import { authApi } from './api/authApi';
import { toastError } from './utils/feedback';
import type { IncomingScreenPopPayload, WsMessage } from './types/telephony';

// 核心规则：未接待回拨记录的优先级最高，首屏默认展示 callback
const activeTab = ref<'callback' | 'records' | 'agents'>('callback');
const showWsModal = ref(false);
const outboundBarRef = ref<InstanceType<typeof OutboundBar> | null>(null);
const softphoneDialerRef = ref<InstanceType<typeof SoftphoneDialer> | null>(null);

const agentStore = useAgentStore();
const callStore = useCallStore();
const cdrStore = useCdrStore();

let unsubscribeWs: (() => void) | null = null;

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'F2') {
    e.preventDefault();
    activeTab.value = activeTab.value === 'agents' ? 'callback' : 'agents';
  }
}

let registrationGeneration = 0;
async function syncWebRtcRegistration() {
  const generation = ++registrationGeneration;
  if (agentStore.isLoggedIn && agentStore.endpoint === 'WEBRTC') {
    const extension = agentStore.boundSipExtension || agentStore.extension;
    if (!extension) {
      sipWebRtcService.init('', null);
      return;
    }
    try {
      const result = await authApi.sipConfig();
      if (generation !== registrationGeneration || !agentStore.isLoggedIn || agentStore.endpoint !== 'WEBRTC') return;
      sipWebRtcService.init(result.data.extension, { ...result.data, iceServers: getRuntimeConfig().iceServers });
    } catch (error) {
      if (generation !== registrationGeneration) return;
      toastError('无法取得本人 SIP 配置，请检查分机开通及接入配置');
      sipWebRtcService.init(extension, null);
    }
  } else {
    registrationGeneration++;
    sipWebRtcService.destroy();
  }
}

// 监听登录状态与接听方式流转
watch(() => agentStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    wsService.connect(agentStore.workNo);
    agentStore.loadEndpoints();
    syncWebRtcRegistration();
  } else {
    registrationGeneration++;
    wsService.disconnect();
    sipWebRtcService.destroy();
  }
});

watch([() => agentStore.endpoint, () => agentStore.boundSipExtension], () => {
  syncWebRtcRegistration();
});

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown);

  // 仅在已登录状态下启动 WebSocket 信道与加载接听资产
  if (agentStore.isLoggedIn) {
    wsService.connect(agentStore.workNo);
    agentStore.loadEndpoints();
    syncWebRtcRegistration();
  }

  // 绑定 WebRTC 呼叫信令钩子
  sipWebRtcService.onIncomingCall((session, caller) => {
    if (callStore.callState !== 'RINGING') {
      // 软电话侧只掌握 INVITE 带来的真实主叫号码，其余弹屏字段由后端按事实补齐
      callStore.triggerIncoming({
        callId: `sip-${session.id}`,
        callerNumber: caller,
      });
    }
  });

  sipWebRtcService.onCallConnected(() => {
    callStore.observeAnswered();
  });

  sipWebRtcService.onCallEnded((_cause) => {
    callStore.observeEnded();
  });

  // 监听后端推送的真实话务事件
  unsubscribeWs = wsService.subscribe((msg: WsMessage) => {
    if (msg.type === 'SCREEN_POP' && msg.data) {
      void agentStore.refreshStatus();
      callStore.triggerIncoming(msg.data as IncomingScreenPopPayload);
    } else if (msg.type === 'CALL_ANSWERED') {
      callStore.observeAnswered(msg.callId);
    } else if (msg.type === 'CALL_HANGUP') {
      callStore.observeEnded(msg.callId);
      void agentStore.refreshStatus();
    } else if (msg.type === 'CHANNEL_READY') {
      void agentStore.refreshStatus();
    }
  });
});

onUnmounted(() => {
  registrationGeneration++;
  window.removeEventListener('keydown', handleKeyDown);
  if (unsubscribeWs) unsubscribeWs();
  wsService.disconnect();
  sipWebRtcService.destroy();
});

function handleToggleSoftphone() {
  if (softphoneDialerRef.value) {
    softphoneDialerRef.value.toggleOpen();
  }
}

function handleTriggerOutbound(phone: string) {
  if (softphoneDialerRef.value && agentStore.endpoint === 'WEBRTC') {
    softphoneDialerRef.value.openWithNumber(phone);
  } else if (outboundBarRef.value) {
    outboundBarRef.value.setOutboundPhone(phone);
  }
}

function handleViewAgentRecords(_workNo: string) {
  activeTab.value = 'records';
  cdrStore.searchCaller = '';
  cdrStore.loadRecords(1);
}
</script>
