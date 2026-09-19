import apiClient from './apiClient';
import type { PageResult } from './extensionApi';

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
}

export interface CallbackTaskQueryReq {
  pageNum?: number;
  pageSize?: number;
  status?: string;
  customerNumber?: string;
}

export interface CallbackTaskAssignReq {
  agentWorkNo?: string;
  agentName: string;
}

export const callbackApi = {
  list(params?: CallbackTaskQueryReq): Promise<PageResult<CallbackTaskVO>> {
    return apiClient.get('/callbacks', { params });
  },
  assign(id: string | number, data: CallbackTaskAssignReq): Promise<void> {
    return apiClient.post(`/callbacks/${id}/assign`, data);
  },
  call(id: string | number): Promise<void> {
    return apiClient.post(`/callbacks/${id}/call`);
  },
};
