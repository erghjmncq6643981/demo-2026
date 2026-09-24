import type { AgentWorkStatus, CallState } from '../../../types/telephony';

const labels: Record<CallState, string> = {
  IDLE: '空闲',
  CALLING: '呼叫中',
  RINGING: '振铃中',
  CONNECTED: '通话中',
  ENDING: '结束中',
  ACW: '话后整理',
};

const workStatusLabels: Record<AgentWorkStatus, string> = {
  READY: '空闲',
  UNREADY: '空闲',
  BUSY: '空闲',
  REST: '空闲',
  CALLING: '呼叫中（恢复中）',
  RINGING: '振铃中（恢复中）',
  ANSWERED: '通话中（恢复中）',
  ACW: '话后整理',
};

/** 展示本机通话状态；重连后本机尚未恢复时使用服务端工作状态兜底。 */
export function callStateLabel(state: CallState, runtime: AgentWorkStatus): string {
  if (state !== 'IDLE') return labels[state];
  return workStatusLabels[runtime];
}

export function callStateTone(state: CallState, runtime: AgentWorkStatus): string {
  const effective = state === 'IDLE' ? runtime : state;
  if (effective === 'CONNECTED' || effective === 'CALLING' || effective === 'RINGING' || effective === 'ANSWERED') {
    return 'danger';
  }
  if (effective === 'ENDING' || effective === 'ACW') return 'warning';
  return 'neutral';
}
