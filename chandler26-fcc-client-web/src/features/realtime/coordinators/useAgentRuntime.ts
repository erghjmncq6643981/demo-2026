import { onMounted, onUnmounted, watch } from 'vue';
import { useAgentStore } from '../../../stores/agentStore';
import { useCallStore } from '../../../stores/callStore';
import { useCdrStore } from '../../../stores/cdrStore';
import { wsService } from '../../../services/websocketService';
import { sipWebRtcService } from '../../../services/sipWebRtcService';
import { getRuntimeConfig } from '../../../shared/config/runtimeConfig';
import { toastError } from '../../../utils/feedback';
import { routeAgentEvent } from './agentEventRouter';

/** Owns login/bootstrap, WS subscription and SIP hooks for one mounted desktop shell. */
export function useAgentRuntime() {
  const agent = useAgentStore();
  const call = useCallStore();
  const records = useCdrStore();
  let registrationGeneration = 0;
  let bootstrapGeneration = 0;
  let disposed = false;
  const subscriptions: Array<() => void> = [];

  async function syncRegistration() {
    const generation = ++registrationGeneration;
    if (!agent.isLoggedIn) { sipWebRtcService.destroy(); return; }
    const config = agent.sipConfig || await agent.loadSipConfig();
    if (disposed || generation !== registrationGeneration || !agent.isLoggedIn) return;
    if (config) {
      if (!sipWebRtcService.isRegistered.value && sipWebRtcService.registrationState.value !== 'CONNECTING') {
        sipWebRtcService.init(config.extension, { ...config, iceServers: getRuntimeConfig().iceServers });
      }
    } else {
      toastError('无法取得本人 SIP 配置，请检查分机开通及接入配置');
      sipWebRtcService.init(agent.boundSipExtension || agent.extension || '', null);
    }
  }

  async function bootstrap() {
    const generation = ++bootstrapGeneration;
    const stillCurrent = () => !disposed && agent.isLoggedIn && generation === bootstrapGeneration;
    wsService.connect(agent.workNo);
    await agent.loadEndpoints();
    if (!stillCurrent()) return;
    await agent.loadSipConfig();
    if (!stillCurrent()) return;
    await agent.refreshRuntimeState();
    if (stillCurrent()) await syncRegistration();
  }

  watch(() => agent.isLoggedIn, (loggedIn) => {
    if (loggedIn) void bootstrap();
    else {
      bootstrapGeneration++;
      registrationGeneration++;
      wsService.disconnect();
      sipWebRtcService.destroy();
    }
  });
  watch(() => agent.endpoint, () => { void syncRegistration(); });

  onMounted(() => {
    subscriptions.push(
      sipWebRtcService.onIncomingCall(({ callId }) => call.handleIncomingSipSession(callId)),
      sipWebRtcService.onCallConnected(({ callId }) => {
        const active = callId || call.currentCall?.callId;
        if (active) call.observeAnswered(active);
      }),
      sipWebRtcService.onCallEnded(({ callId }) => {
        const active = callId || call.currentCall?.callId;
        if (active) call.observeEnded(active);
      }),
      wsService.subscribe((message) => routeAgentEvent(message, {
        refreshPresence: () => { void agent.refreshRuntimeState(); },
        incoming: call.triggerIncoming,
        answered: call.observeAnswered,
        ended: call.observeEnded,
        released: call.observeReleased,
        reloadRecords: () => { void records.loadRecords(1); },
      })),
    );
    if (agent.isLoggedIn) void bootstrap();
  });

  onUnmounted(() => {
    disposed = true;
    bootstrapGeneration++;
    registrationGeneration++;
    subscriptions.splice(0).forEach((unsubscribe) => unsubscribe());
    wsService.disconnect();
    sipWebRtcService.destroy();
    call.resetCall();
  });
}
