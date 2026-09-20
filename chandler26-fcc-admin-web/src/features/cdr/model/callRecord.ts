import type { CallCdrVO } from "../../../api/cdrApi";

export interface CallRecord {
  id: string; // 通话ID (Call ID)
  callerName: string; // 主叫姓名
  callerPhone: string; // 主叫号码
  carrier: string; // 运营商事实，后端未提供时留空
  agentName: string; // 坐席姓名
  agentWorkNo: string; // 坐席工号
  direction: "INBOUND" | "OUTBOUND" | "INTERNAL"; // 方向: 呼入 / 呼出 / 内部
  startTime: string; // 通话开始时间
  endTime: string; // 通话结束时间
  ringDuration: string; // 响铃时长
  audioDuration?: string; // 录音（显示时长）
  recordingUrl?: string; // 真实流式录音地址
  rawId?: string; // 后端数据库 ID（不透明字符串）
  status: "ANSWERED" | "MISSED" | "BUSY" | "REJECTED"; // 状态: 已接通 / 未接听 / 坐席忙 / 拒接
  modelType: string; // 业务通话模型
  flowCode?: string; // 实际绑定的流程编码
  satisfactionScore?: number; // 满意度评分 (1-5)
}

/** Map backend facts without inventing measurements or customer data. */
export function toCallRecord(item: CallCdrVO): CallRecord {
  const ringSec =
    item.waitDurationMs == null ? null : Math.round(item.waitDurationMs / 1000);
  const audioDuration = item.audioDuration;

  // 后端话单状态已归一化为 ANSWERED / NO_ANSWER，此处必须显式兜底为「未接听」，
  // 否则后续状态码（如 NO_ANSWER / TIMEOUT）会落到默认分支被误标成「已接通」，
  // 进而把接通率算成 100%。
  let status: "ANSWERED" | "MISSED" | "BUSY" | "REJECTED" = "MISSED";
  if (item.status === "ANSWERED") status = "ANSWERED";
  else if (item.status === "BUSY") status = "BUSY";
  else if (item.status === "REJECTED" || item.status === "CANCELLED")
    status = "REJECTED";
  else status = "MISSED";

  return {
    id: item.ctrlId || `CDR-${item.id}`,
    callerName: item.callerName || "",
    callerPhone: item.caller,
    carrier: item.carrier || "",
    agentName:
      item.agentName ||
      (item.agentWorkNo ? `坐席 ${item.agentWorkNo}` : "未分配"),
    agentWorkNo: item.agentWorkNo || "-",
    direction: (item.direction as any) || "INBOUND",
    startTime: item.initiatedAt || item.answeredAt || "-",
    endTime: item.endedAt || "-",
    ringDuration: ringSec == null ? "-" : `${ringSec}秒`,
    audioDuration: audioDuration,
    recordingUrl: item.recordingUrl,
    rawId: item.id,
    status: status,
    modelType: item.modelType,
    flowCode: item.flowCode,
    satisfactionScore: item.evaluationScore,
  };
}
