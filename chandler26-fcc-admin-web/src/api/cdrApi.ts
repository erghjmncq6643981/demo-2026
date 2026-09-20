import apiClient from "./apiClient";
import type { PageResult } from "../shared/api/page";

export interface CallLegVO {
  id: string;
  legType: string;
  legChannelId: string;
  destination: string;
  hangupCause?: string;
  startAt?: string;
  answerAt?: string;
  endAt?: string;
}

export interface CallCdrVO {
  id: string;
  ctrlId: string;
  bizId?: string;
  modelType: string;
  flowCode?: string;
  routeTargetType?: string;
  routeTargetId?: string;
  direction: string;
  caller: string;
  callerName?: string;
  carrier?: string;
  callee: string;
  didNumber?: string;
  status: string;
  hangupCause?: string;
  agentWorkNo?: string;
  agentName?: string;
  extension?: string;
  waitDurationMs?: number;
  talkDurationMs?: number;
  audioDuration?: string;
  totalDurationMs?: number;
  evaluationScore?: number;
  evaluationDetails?: string;
  recordingUrl?: string;
  initiatedAt?: string;
  answeredAt?: string;
  endedAt?: string;
  legs?: CallLegVO[];
}

export interface CdrStatsVO {
  totalCalls: number;
  answeredCalls: number;
  totalTalkSec: number;
  inboundTalkSec: number;
  outboundTalkSec: number;
}

export interface CallCdrQueryReq {
  pageNum?: number;
  pageSize?: number;
  /** 号码关键字：主叫或被叫模糊匹配 */
  number?: string;
  caller?: string;
  callee?: string;
  agentWorkNo?: string;
  agentName?: string;
  /** 通话ID / 控制标识 ctrl_id */
  ctrlId?: string;
  status?: string;
  direction?: string;
  /** 通话开始时间下界 (ISO: 2026-09-19T00:00:00) */
  startTime?: string;
  /** 通话开始时间上界 (ISO: 2026-09-19T23:59:59) */
  endTime?: string;
}

export const cdrApi = {
  list(params?: CallCdrQueryReq): Promise<PageResult<CallCdrVO>> {
    return apiClient.get("/cdrs", { params });
  },
  getDetail(id: string): Promise<CallCdrVO> {
    return apiClient.get(`/cdrs/${id}`);
  },
  /** 今日话单 KPI 聚合指标 (由数据库聚合，与列表分页无关) */
  stats(): Promise<CdrStatsVO> {
    return apiClient.get("/cdrs/stats");
  },
};
