import { expect, it, vi } from 'vitest';
import { routeAgentEvent, type AgentEventPorts } from './agentEventRouter';
import type { WsMessage } from '../../../types/telephony';

function ports(): AgentEventPorts {
  return { refreshPresence: vi.fn(), incoming: vi.fn(), answered: vi.fn(), ended: vi.fn(), released: vi.fn(), reloadRecords: vi.fn() };
}

function message(type: WsMessage['type'], data?: unknown): WsMessage {
  return { type, workNo: 'test-agent', callId: '9007199254740993', timestamp: 0, data };
}

it('preserves opaque call identity and keeps command acknowledgement out of final state', () => {
  const actions = ports();
  routeAgentEvent(message('CALL_ANSWERED'), actions);
  expect(actions.answered).toHaveBeenCalledWith('9007199254740993');
  routeAgentEvent(message('CALL_CONTROL_ACTION'), actions);
  expect(actions.ended).not.toHaveBeenCalled();
});

it('only an explicitly correlated release fact ends a call, not generic idle or busy', () => {
  const actions = ports();
  routeAgentEvent(message('AGENT_PRESENCE_CHANGE', { workStatus: 'BUSY' }), actions);
  expect(actions.released).not.toHaveBeenCalled();
  routeAgentEvent(message('AGENT_PRESENCE_CHANGE', { workStatus: 'READY' }), actions);
  expect(actions.released).not.toHaveBeenCalled();
  routeAgentEvent(message('AGENT_PRESENCE_CHANGE', { reason: 'AGENT_LEG_ENDED', workStatus: 'BUSY' }), actions);
  expect(actions.released).toHaveBeenCalledWith('9007199254740993');
  expect(actions.reloadRecords).toHaveBeenCalledOnce();
});

it('routes only real screen pops and refreshes presence after hangup', () => {
  const actions = ports();
  routeAgentEvent(message('SCREEN_POP'), actions);
  expect(actions.incoming).not.toHaveBeenCalled();
  const payload = { callId: '9007199254740993', direction: 'INBOUND' };
  routeAgentEvent(message('SCREEN_POP', payload), actions);
  expect(actions.incoming).toHaveBeenCalledWith(payload);
  routeAgentEvent(message('CALL_HANGUP'), actions);
  expect(actions.ended).toHaveBeenCalledWith('9007199254740993');
  expect(actions.refreshPresence).toHaveBeenCalledTimes(2);
});
