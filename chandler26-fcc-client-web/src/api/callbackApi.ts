import { adminApi } from './apiClient';

export interface CallbackTaskVO {
  id: string;
  phone: string;
  time: string;
  reason: string;
  duration: string;
  status: 'PENDING' | 'ASSIGNED' | 'CALLED';
  assignee?: string;
  assigneeWorkNo?: string;
  callAttempts?: number;
  lastCalledAt?: string;
  notes?: string;
  customerName?: string;
  customerType?: string;
  didNumber?: string;
}

export interface CallbackTaskQueryReq {
  pageNum?: number;
  pageSize?: number;
  status?: string;
  customerNumber?: string;
}

export interface CallbackPageResult {
  pageNum: number;
  pageSize: number;
  total: number;
  list: CallbackTaskVO[];
}

interface CommonResult<T> {
  code: number;
  message: string;
  data: T;
}

export const callbackApi = {
  async list(params?: CallbackTaskQueryReq): Promise<CallbackPageResult> {
    const res = (await adminApi.get('/callbacks', { params })) as unknown as CommonResult<CallbackPageResult>;
    return res.data;
  },
  async assign(id: string | number, agentName: string, agentWorkNo?: string): Promise<void> {
    await adminApi.post(`/callbacks/${id}/assign`, { agentName, agentWorkNo });
  },
  async call(id: string | number): Promise<void> {
    await adminApi.post(`/callbacks/${id}/call`);
  },
};
