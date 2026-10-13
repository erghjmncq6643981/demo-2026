import { executionLabels, type StageExecution } from './stagedFlow';

/** Command handling success never proves answer, bridge, recording completion or binding. */
export function executionEvidence(execution?: StageExecution) {
  const state = execution?.status;
  return {
    statusText: state ? (executionLabels[state] || `执行状态：${state}`) : '暂无执行记录',
    badgeClass: state === 'FAILED' || state === 'INTERRUPTED'
      ? 'bg-rose-50 text-rose-700 border-rose-200'
      : 'bg-slate-50 text-slate-600 border-slate-200',
    startedAt: execution?.startedAt || '-',
    durationText: execution?.durationMs != null ? `${execution.durationMs} ms` : '-',
  };
}

/** Only an explicit boolean business fact establishes the result of binding. */
export function bindingOutcome(facts: Record<string, unknown>): boolean | undefined {
  return typeof facts.bindingAccepted === 'boolean' ? facts.bindingAccepted : undefined;
}
