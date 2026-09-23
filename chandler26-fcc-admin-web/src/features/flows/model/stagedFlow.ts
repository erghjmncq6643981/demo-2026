export const stageLabels: Record<string, string> = {
  ENTRY: "呼入入口",
  MENU: "欢迎语与按键收号",
  BRANCH: "按键条件分支",
  ROUTE: "坐席与技能组排队",
  DIAL_AGENT: "呼叫坐席",
  DIAL_CUSTOMER: "呼叫客户",
  BRIDGE: "请求双方桥接",
  RECORD_START: "开始录音",
  CONNECTED: "通话中",
  RECORD_STOP: "停止录音",
  RATING: "服务评价",
  RATING_SAVE: "保存评价",
  CLOSING: "结束语音",
  NOTIFY: "通知放音与收号",
  CONFIRM: "客户确认",
  END: "结束与回拨处理",
};
export const inboundStages = [
  "ENTRY",
  "MENU",
  "BRANCH",
  "ROUTE",
  "BRIDGE",
  "RECORD_START",
  "CONNECTED",
  "RECORD_STOP",
  "RATING",
  "RATING_SAVE",
  "CLOSING",
  "END",
] as const;
export const configurableStages = new Set(["MENU", "BRANCH", "ROUTE", "END"]);
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
export interface FlowValidationIssue {
  stage: string;
  field: string;
  message: string;
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
    stages: [...inboundStages],
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

/** 在提交服务端前给出可定位到节点和字段的维护错误。 */
export function validateStagedFlow(flow: StagedFlow): FlowValidationIssue[] {
  const issues: FlowValidationIssue[] = [];
  const add = (stage: string, field: string, message: string) =>
    issues.push({ stage, field, message });
  if (flow.routeMode !== "IVR" || flow.template !== "INBOUND") {
    add("ENTRY", "template", "仅支持呼入 IVR 固定模型");
  }
  if (
    flow.stages.length !== inboundStages.length ||
    flow.stages.some((stage, index) => stage !== inboundStages[index])
  ) {
    add("ENTRY", "stages", "固定动作目录不完整，请重新创建草稿");
  }
  if (!flow.menu || typeof flow.menu.enabled !== "boolean") {
    add("MENU", "enabled", "必须明确是否启用语音菜单");
  } else {
    if (!Number.isInteger(flow.menu.timeoutSeconds) || flow.menu.timeoutSeconds < 3 || flow.menu.timeoutSeconds > 60) {
      add("MENU", "timeoutSeconds", "等待按键时间应为 3 至 60 秒");
    }
    if (flow.menu.enabled && !flow.menu.prompt.trim()) {
      add("MENU", "prompt", "启用菜单后必须填写导航文案或预置音频路径");
    }
    if (flow.menu.prompt.length > 1000) {
      add("MENU", "prompt", "导航文案不能超过 1000 个字符");
    }
  }
  const branches = flow.branches || [];
  if (branches.length > 10 || (flow.menu?.enabled && branches.length < 1)) {
    add("BRANCH", "branches", "启用菜单时需要 1 至 10 个按键分支");
  }
  const digits = new Set<string>();
  branches.forEach((branch, index) => {
    if (!/^[0-9]$/.test(branch.digit)) {
      add("BRANCH", `branches.${index}.digit`, `第 ${index + 1} 个分支必须填写一位数字`);
    } else if (digits.has(branch.digit)) {
      add("BRANCH", `branches.${index}.digit`, `按键 ${branch.digit} 重复`);
    }
    digits.add(branch.digit);
    validateTarget(branch, "BRANCH", `第 ${index + 1} 个分支`, add);
  });
  validateTarget(flow.defaultRoute, "BRANCH", "else 默认分支", add);
  if (flow.timeoutAction !== "CALLBACK" && flow.timeoutAction !== "HANGUP") {
    add("END", "timeoutAction", "请选择超时后创建回拨待办或直接挂机");
  }
  return issues;
}

function validateTarget(
  target: FlowTarget | undefined,
  stage: string,
  label: string,
  add: (stage: string, field: string, message: string) => void,
) {
  if (!target || (target.targetType !== "AGENT" && target.targetType !== "GROUP")) {
    add(stage, "targetType", `${label}的目标类型无效`);
    return;
  }
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(target.target)) {
    add(stage, "target", `${label}必须填写有效的坐席工号或技能组代码`);
  }
  if (!Number.isInteger(target.queueSeconds) || target.queueSeconds < 5 || target.queueSeconds > 300) {
    add(stage, "queueSeconds", `${label}的排队时限应为 5 至 300 秒`);
  }
}
export const executionLabels: Record<string, string> = {
  WAITING: "等待事件",
  SUCCEEDED: "已完成",
  INTERRUPTED: "已中断",
  FAILED: "失败",
  UNKNOWN: "结果未知",
};
