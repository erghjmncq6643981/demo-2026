/**
 * 话务信令与呼叫状态领域类型定义
 *
 * 说明：WebSocket 报文类型与后端 WsMessageTypeEnum 的 code 严格一一对应，
 * 前端不再自造别名（历史上曾出现后端推 SCREEN_POP、前端监听 CALL_STATE_CHANGE 之类的错位）。
 */

export type CallState = 'IDLE' | 'CALLING' | 'RINGING' | 'CONNECTED' | 'ENDING' | 'ACW';

/** 坐席业务登录状态：示忙只屏蔽呼入，仍允许主动外呼。 */
export type AgentLoginStatus = 'LOGIN' | 'LOGIN_BUSY' | 'LOGOUT';

/** 服务端权威工作状态，包含空闲基态和人工通话生命周期。 */
export type AgentWorkStatus =
  | 'READY'
  | 'UNREADY'
  | 'BUSY'
  | 'REST'
  | 'CALLING'
  | 'RINGING'
  | 'ANSWERED'
  | 'ACW';

export type AnswerEndpointType = 'WEBRTC' | 'SIP' | 'MOBILE';

/**
 * 坐席话务弹屏载荷
 *
 * 所有字段均来自后端真实事实来源，取不到即为 undefined；
 * 界面需对缺失字段展示中性占位，不得假定存在客户档案。
 */
export interface IncomingScreenPopPayload {
  /** 业务通话唯一标识 (FCC call_id) */
  callId: string;
  /** 呼叫方向 */
  direction?: 'INBOUND' | 'OUTBOUND';
  /** 客户侧号码：呼入为主叫，呼出为被叫 */
  callerNumber?: string;
  /** 接入线路号码：呼入为热线 DID，呼出为坐席外呼主叫号 */
  didNumber?: string;
  /** 振铃超时秒数 */
  ringTimeoutSeconds?: number;
  /** 命中的话务流程名称 (来自流程配置事实) */
  flowName?: string;
  /** 真实 IVR / 流程轨迹 */
  ivrPath?: string;
  /** 真实路由依据 */
  routingReason?: string;
  /** 客户姓名 (仅坐席外呼时录入值) */
  customerName?: string;
  /** 客户单位 (仅坐席外呼时录入值) */
  companyName?: string;
  /** 同号码前序接待坐席姓名 */
  lastAgentName?: string;
  /** 同号码前序接待坐席工号 */
  lastAgentWorkNo?: string;
  /** 前序通话时间 */
  lastCallTime?: string;
  /** 前序通话摘要 */
  lastCallSummary?: string;
  /** 前序通话录音复播地址 (仅当确实存在录音时给出) */
  lastRecordingUrl?: string;
}

/** 后端 WsMessageTypeEnum 的 code 取值 */
export type WsMessageType =
  | 'HEARTBEAT_PING'
  | 'HEARTBEAT_PONG'
  | 'CHANNEL_READY'
  | 'SCREEN_POP'
  | 'CALL_ANSWERED'
  | 'CALL_HANGUP'
  | 'AGENT_PRESENCE_CHANGE'
  | 'CALL_CONTROL_ACTION';

export interface WsMessage<T = unknown> {
  type: WsMessageType;
  workNo: string;
  callId?: string;
  timestamp: number;
  traceId?: string;
  data?: T;
}
