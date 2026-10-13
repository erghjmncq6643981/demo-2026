import type { IncomingScreenPopPayload, WsMessage } from '../../../types/telephony';

/** Business events enter one call-state owner; transports never mutate page state. */
export interface AgentEventPorts {
  refreshPresence(): void;
  incoming(payload: IncomingScreenPopPayload): void;
  answered(callId?: string): void;
  ended(callId?: string): void;
  released(callId?: string): void;
  reloadRecords(): void;
}

/** Translate the business WebSocket envelope without merging SIP or connectivity state. */
export function routeAgentEvent(message: WsMessage, ports: AgentEventPorts): void {
  switch (message.type) {
    case 'SCREEN_POP':
      if (message.data) {
        ports.refreshPresence();
        ports.incoming(message.data as IncomingScreenPopPayload);
      }
      break;
    case 'CALL_ANSWERED': ports.answered(message.callId); break;
    case 'CALL_HANGUP':
      ports.ended(message.callId);
      ports.refreshPresence();
      break;
    case 'CHANNEL_READY': ports.refreshPresence(); break;
    case 'AGENT_PRESENCE_CHANGE': {
      ports.refreshPresence();
      const data = message.data as { workStatus?: string; reason?: string } | undefined;
      if (message.callId && ['AGENT_LEG_ENDED', 'AGENT_CALL_RELEASED', 'CALL_FINALIZED'].includes(data?.reason || '')) {
        ports.released(message.callId);
        ports.reloadRecords();
      }
      break;
    }
  }
}
