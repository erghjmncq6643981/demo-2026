import type { CallState } from '../../../types/telephony';

export interface CallLifecycle {
  state: CallState;
  callId: string | null;
}

export type CallLifecycleEvent =
  | { type: 'INCOMING'; callId: string }
  | { type: 'OUTBOUND_STARTED'; callId: string }
  | { type: 'ANSWERED'; callId?: string }
  | { type: 'HANGUP_REQUESTED'; callId?: string }
  | { type: 'ENDED'; callId?: string }
  | { type: 'REJECTED'; callId?: string }
  | { type: 'ACW_COMPLETED' }
  | { type: 'RESET' };

const INITIAL_LIFECYCLE: CallLifecycle = { state: 'IDLE', callId: null };

function eventBelongsToActiveCall(current: CallLifecycle, callId?: string): boolean {
  if (!current.callId) return true;
  return Boolean(callId) && current.callId === callId;
}

/**
 * Apply one monotonic call lifecycle event. Terminal ACW state ignores delayed
 * transport notifications until the agent explicitly completes ACW.
 */
export function reduceCallLifecycle(
  current: CallLifecycle,
  event: CallLifecycleEvent,
): CallLifecycle {
  if (event.type === 'RESET') return INITIAL_LIFECYCLE;
  if (event.type === 'ACW_COMPLETED') {
    return current.state === 'ACW' ? INITIAL_LIFECYCLE : current;
  }

  if (!eventBelongsToActiveCall(current, 'callId' in event ? event.callId : undefined)) {
    return current;
  }

  switch (current.state) {
    case 'IDLE':
      if (event.type === 'INCOMING') return { state: 'RINGING', callId: event.callId };
      if (event.type === 'OUTBOUND_STARTED') return { state: 'CALLING', callId: event.callId };
      return current;
    case 'CALLING':
    case 'RINGING':
      if (event.type === 'ANSWERED') return { ...current, state: 'CONNECTED', callId: event.callId ?? current.callId };
      if (event.type === 'HANGUP_REQUESTED') return { ...current, state: 'ENDING' };
      if (event.type === 'ENDED') return { ...current, state: 'ACW' };
      if (event.type === 'REJECTED') return INITIAL_LIFECYCLE;
      return current;
    case 'CONNECTED':
      if (event.type === 'HANGUP_REQUESTED') return { ...current, state: 'ENDING' };
      if (event.type === 'ENDED') return { ...current, state: 'ACW' };
      return current;
    case 'ENDING':
      if (event.type === 'ENDED') return { ...current, state: 'ACW' };
      return current;
    case 'ACW':
      return current;
  }
}

export function initialCallLifecycle(): CallLifecycle {
  return { ...INITIAL_LIFECYCLE };
}
