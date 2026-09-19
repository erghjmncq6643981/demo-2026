/**
 * 历史通话话单 (CDR) 领域类型定义 (对齐 fcc_call_record 与 CallCdrVO)
 */

export interface CallCdrItem {
  id: string;
  ctrlId?: string;
  bizId: string;
  modelType: string;
  direction: 'INBOUND' | 'OUTBOUND';
  caller: string;
  callerName?: string;
  callee: string;
  didNumber?: string;
  status: string; // 'NORMAL_END', 'CALLING', 'COMPLETED' etc.
  hangupCause?: string;
  agentWorkNo?: string;
  agentName?: string;
  extension?: string;
  waitDurationMs?: number;
  talkDurationMs?: number;
  totalDurationMs?: number;
  evaluationScore?: number;
  recordingUrl?: string;
  initiatedAt: string;
  answeredAt?: string;
  endedAt?: string;
}

export interface CdrPageResult {
  pageNum: number;
  pageSize: number;
  total: number;
  list: CallCdrItem[];
}

export interface CdrQueryParams {
  pageNum?: number;
  pageSize?: number;
  caller?: string;
  callee?: string;
  direction?: string;
  agentWorkNo?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}
