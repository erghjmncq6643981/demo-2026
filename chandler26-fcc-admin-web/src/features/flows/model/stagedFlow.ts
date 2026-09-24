import type { FlowTemplateType } from "../../../api/flowApi";

export const stageLabels: Record<string, string> = {
  ENTRY: "流程入口",
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
  END: "流程结束",
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

interface BaseStagedFlow {
  template: FlowTemplateType;
  stages: string[];
  nodes?: FlowModelNode[];
}

export interface InboundStagedFlow extends BaseStagedFlow {
  routeMode: "IVR";
  template: "INBOUND";
  menu: { enabled: boolean; prompt: string; timeoutSeconds: number };
  branches: FlowBranch[];
  defaultRoute: FlowTarget;
  timeoutAction: "CALLBACK" | "HANGUP";
}

export type StagedFlow = InboundStagedFlow;

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
): boolean {
  return !system && publishStatus === "DRAFT";
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

/** Return the Chinese business label for a maintainable flow type. */
export function flowTemplateLabel(type: FlowTemplateType): string {
  return type === "INBOUND" ? "呼入 IVR" : type;
}

/** Clone an inbound flow and update the default route shared by BRANCH and ROUTE. */
export function updateDefaultRoute(
  source: InboundStagedFlow,
  patch: Partial<FlowTarget>,
): InboundStagedFlow {
  const copy = JSON.parse(JSON.stringify(source)) as InboundStagedFlow;
  copy.defaultRoute = { ...copy.defaultRoute, ...patch };
  return copy;
}

/** Create an inbound draft with no fictional DID, group, or agent. */
export function newInboundFlow(): InboundStagedFlow {
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

/** Create the first draft that matches the immutable flow master-data type. */
export function newStagedFlow(type: FlowTemplateType): StagedFlow {
  if (type !== "INBOUND") throw new Error("当前仅支持创建呼入 IVR 流程");
  return newInboundFlow();
}

/** Read a supported staged definition without guessing legacy models. */
export function readStagedFlow(value: string): StagedFlow | null {
  if (!value.trim()) return null;
  const root = JSON.parse(value) as Record<string, unknown>;
  if (!Array.isArray(root.stages)) {
    throw new Error("该版本没有阶段目录，不能推测画布");
  }
  if (root.template === "INBOUND" && root.routeMode === "IVR") {
    return root as unknown as InboundStagedFlow;
  }
  throw new Error("流程类型或运行模式不受支持");
}

/** Validate editable fields before submitting the shared server-side contract. */
export function validateStagedFlow(flow: StagedFlow): FlowValidationIssue[] {
  return validateInboundFlow(flow);
}

function validateInboundFlow(flow: InboundStagedFlow): FlowValidationIssue[] {
  const issues: FlowValidationIssue[] = [];
  const add = (stage: string, field: string, message: string) =>
    issues.push({ stage, field, message });
  validateStages(flow.stages, inboundStages, add);
  if (!Number.isInteger(flow.menu.timeoutSeconds) || flow.menu.timeoutSeconds < 3 || flow.menu.timeoutSeconds > 60) {
    add("MENU", "timeoutSeconds", "等待按键时间应为 3 至 60 秒");
  }
  if (flow.menu.enabled && !flow.menu.prompt.trim()) {
    add("MENU", "prompt", "启用菜单后必须填写导航文案或预置音频路径");
  }
  if (flow.menu.prompt.length > 1000) {
    add("MENU", "prompt", "导航文案不能超过 1000 个字符");
  }
  if (flow.branches.length > 10 || (flow.menu.enabled && flow.branches.length < 1)) {
    add("BRANCH", "branches", "启用菜单时需要 1 至 10 个按键分支");
  }
  const digits = new Set<string>();
  flow.branches.forEach((branch, index) => {
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

function validateStages(
  actual: string[],
  expected: readonly string[],
  add: (stage: string, field: string, message: string) => void,
) {
  if (
    actual.length !== expected.length ||
    actual.some((stage, index) => stage !== expected[index])
  ) {
    add("ENTRY", "stages", "固定动作目录不完整，请重新创建草稿");
  }
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
