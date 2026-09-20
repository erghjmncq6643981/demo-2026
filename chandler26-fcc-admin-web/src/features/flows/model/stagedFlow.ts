export const stageLabels: Record<string, string> = {
  ENTRY: '呼入入口',
  MENU: '欢迎语与按键收号',
  BRANCH: '按键条件分支',
  ROUTE: '坐席与技能组排队',
  DIAL_AGENT: '呼叫坐席',
  DIAL_CUSTOMER: '呼叫客户',
  BRIDGE: '请求双方桥接',
  CONNECTED: '通话中',
  NOTIFY: '通知放音与收号',
  CONFIRM: '客户确认',
  END: '结束与回拨处理',
};
export interface FlowTarget {
  targetType: 'AGENT' | 'GROUP';
  target: string;
  queueSeconds: number;
}
export interface FlowBranch extends FlowTarget {
  digit: string;
}
export interface StagedFlow {
  routeMode?: string;
  template: string;
  stages: string[];
  menu?: { enabled: boolean; prompt: string; timeoutSeconds: number };
  branches?: FlowBranch[];
  defaultRoute?: FlowTarget;
  timeoutAction?: 'CALLBACK' | 'HANGUP';
}
export interface StageExecution {
  id: string;
  stepKey: string;
  actionType: string;
  attemptNo: number;
  status: string;
  startedAt: string;
  endedAt?: string;
  durationMs?: number;
  eventId?: string;
  commandId?: string;
  input?: string;
  output?: string;
  errorCode?: string;
}
/** 新草稿只有结构，无虚构号码、技能组或坐席。 */
export function newInboundFlow(): StagedFlow {
  return {
    routeMode: 'IVR',
    template: 'INBOUND',
    stages: ['ENTRY', 'MENU', 'BRANCH', 'ROUTE', 'BRIDGE', 'CONNECTED', 'END'],
    menu: { enabled: false, prompt: '', timeoutSeconds: 10 },
    branches: [],
    defaultRoute: { targetType: 'AGENT', target: '', queueSeconds: 120 },
    timeoutAction: 'CALLBACK',
  };
}
/** 直达配置仍是合法业务能力；编辑时明确转换成新的阶段定义。 */
export function readStagedFlow(value: string): StagedFlow | null {
  if (!value.trim()) return null;
  const root = JSON.parse(value);
  if (root.routeMode === 'DID_DIRECT') {
    const flow = newInboundFlow();
    flow.defaultRoute!.target = root.didDirectConfig.workNo;
    return flow;
  }
  if (!Array.isArray(root.stages)) throw new Error('该版本没有阶段目录，不能推测画布');
  return root as StagedFlow;
}
export const executionLabels: Record<string, string> = {
  WAITING: '等待事件',
  SUCCEEDED: '已完成',
  INTERRUPTED: '已中断',
  FAILED: '失败',
  UNKNOWN: '结果未知',
};
