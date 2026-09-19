import { telephonyApi } from './apiClient';

interface CommonResult<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * 触发真实智能外呼 (fcc-server :8085)
 */
export async function triggerOutboundCall(
  workNo: string,
  callerPhone: string,
  calleePhone: string,
  customerName?: string,
  companyName?: string
): Promise<CommonResult<{ callId: string; ctrlId: string }>> {
  const res = (await telephonyApi.post('/call/outbound', {
    workNo,
    callerPhone,
    calleePhone,
    customerName,
    companyName,
  })) as unknown as CommonResult<{ callId: string; ctrlId: string }>;
  return res;
}

/**
 * 挂机拆线 (fcc-server :8085)
 */
export async function triggerHangupCall(
  workNo: string,
  callId?: string,
  reason?: string
): Promise<CommonResult<unknown>> {
  const res = (await telephonyApi.post('/call/hangup', {
    workNo,
    callId,
    reason: reason || 'NORMAL_CLEARING',
  })) as unknown as CommonResult<unknown>;
  return res;
}

/**
 * 通话保持 / 恢复 (fcc-server :8085)
 */
export async function triggerHoldCall(
  workNo: string,
  callId: string,
  hold: boolean
): Promise<CommonResult<{ isHeld: boolean }>> {
  const res = (await telephonyApi.post('/call/hold', {
    workNo,
    callId,
    hold,
  })) as unknown as CommonResult<{ isHeld: boolean }>;
  return res;
}

/**
 * 通话中二次 DTMF 发送 (fcc-server :8085)
 */
export async function triggerDtmfCall(
  workNo: string,
  callId: string,
  digit: string
): Promise<CommonResult<{ digit: string }>> {
  const res = (await telephonyApi.post('/call/dtmf', {
    workNo,
    callId,
    digit,
  })) as unknown as CommonResult<{ digit: string }>;
  return res;
}

/**
 * 班长席干预调度 (fcc-server :8085)
 */
export async function triggerSuperviseCall(
  supervisorWorkNo: string,
  targetWorkNo: string,
  type: 'SPY' | 'COACH' | 'BARGE' | 'KILL',
  callId?: string
): Promise<CommonResult<{ action: string; target: string; status: string }>> {
  const res = (await telephonyApi.post('/call/supervise', {
    supervisorWorkNo,
    targetWorkNo,
    type,
    callId,
  })) as unknown as CommonResult<{ action: string; target: string; status: string }>;
  return res;
}

/**
 * 呼叫转接 (fcc-server :8085)
 */
export async function triggerTransferCall(
  workNo: string,
  targetNumber: string,
  callId?: string
): Promise<CommonResult<{ target: string; executed: boolean }>> {
  const res = (await telephonyApi.post('/call/transfer', {
    workNo,
    targetNumber,
    callId,
  })) as unknown as CommonResult<{ target: string; executed: boolean }>;
  return res;
}
