export const stageLabels: Record<string, string> = {
  ENTRY: "呼入入口",
  MENU: "欢迎语与按键收号",
  BRANCH: "按键条件分支",
  ROUTE: "坐席与技能组排队",
  DIAL_AGENT: "呼叫坐席",
  DIAL_CUSTOMER: "呼叫客户",
  BRIDGE: "请求双方桥接",
  CONNECTED: "通话中",
  NOTIFY: "通知放音与收号",
  CONFIRM: "客户确认",
  END: "结束与回拨处理",
};
export interface FlowTarget {
  targetType: "AGENT" | "GROUP";
  target: string;
  queueSeconds: number;
}
export interface FlowBranch extends FlowTarget {
  digit: string;
}
export interface StagedFlow {
  routeMode: "IVR";
  template: string;
  stages: string[];
  menu?: { enabled: boolean; prompt: string; timeoutSeconds: number };
  branches?: FlowBranch[];
  defaultRoute?: FlowTarget;
  timeoutAction?: "CALLBACK" | "HANGUP";
  nodes?: FlowModelNode[];
}
export interface FlowModelNode {
  key: string;
  label: string;
  action: string;
  actionLabel: string;
  executorType: string;
  executorTypeLabel: string;
  operation: string;
  fNodeMethod?: string;
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

/** Return whether the selected flow version can be changed on the canvas. */
export function canEditFlowVersion(
  system: boolean,
  publishStatus: string | undefined,
  workingCopy: boolean,
): boolean {
  return !system && (workingCopy || publishStatus === "DRAFT");
}

/** Return the Chinese status used by the version selector and workspace. */
export function flowVersionStatusLabel(status: string): string {
  return (
    {
      DRAFT: "草稿",
      PUBLISHED: "已发布",
      ARCHIVED: "历史版本",
    }[status] || status
  );
}

/** Clone a flow and update the single default route shared by BRANCH else and ROUTE. */
export function updateDefaultRoute(
  source: StagedFlow,
  patch: Partial<FlowTarget>,
): StagedFlow {
  const copy = JSON.parse(JSON.stringify(source)) as StagedFlow;
  copy.defaultRoute = {
    targetType: "GROUP",
    target: "",
    queueSeconds: 120,
    ...copy.defaultRoute,
    ...patch,
  };
  return copy;
}
/** 新草稿只有结构，无虚构号码、技能组或坐席。 */
export function newInboundFlow(): StagedFlow {
  return {
    routeMode: "IVR",
    template: "INBOUND",
    stages: ["ENTRY", "MENU", "BRANCH", "ROUTE", "BRIDGE", "CONNECTED", "END"],
    menu: { enabled: false, prompt: "", timeoutSeconds: 10 },
    branches: [],
    defaultRoute: { targetType: "AGENT", target: "", queueSeconds: 120 },
    timeoutAction: "CALLBACK",
  };
}
/** 读取新系统的固定阶段 IVR 定义，不接受旧路由模型。 */
export function readStagedFlow(value: string): StagedFlow | null {
  if (!value.trim()) return null;
  const root = JSON.parse(value);
  if (root.routeMode !== "IVR") throw new Error("仅支持固定阶段 IVR 模型");
  if (!Array.isArray(root.stages))
    throw new Error("该版本没有阶段目录，不能推测画布");
  return root as StagedFlow;
}
export const executionLabels: Record<string, string> = {
  WAITING: "等待事件",
  SUCCEEDED: "已完成",
  INTERRUPTED: "已中断",
  FAILED: "失败",
  UNKNOWN: "结果未知",
};
