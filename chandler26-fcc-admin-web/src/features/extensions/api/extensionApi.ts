import apiClient from '../../../api/apiClient';
import type { PageResult } from '../../../shared/api/page';

export interface ExtensionVO {
  id: string;
  extension: string;
  endpointType: string;
  status: string;
  onlineStatus: string;
  registeredContact?: string;
  registeredIp?: string;
  boundAgentName?: string;
  boundAgentWorkNo?: string;
  createdAt?: string;
}

export interface ExtensionCreateReq {
  extension: string;
  password: string;
  endpointType?: string;
}

export interface ExtensionQueryReq {
  pageNum?: number;
  pageSize?: number;
  extension?: string;
  endpointType?: string;
  status?: string;
  onlineStatus?: string;
}

export interface IvrBindResultVO {
  success: boolean;
  code: number;
  extension: string;
  workNo: string;
  agentName?: string;
  promptMessage: string;
}

export const extensionApi = {
  list(params?: ExtensionQueryReq): Promise<PageResult<ExtensionVO>> {
    return apiClient.get('/extensions', { params });
  },
  create(data: ExtensionCreateReq): Promise<string> {
    return apiClient.post('/extensions', data);
  },
  delete(id: string): Promise<void> {
    return apiClient.delete(`/extensions/${id}`);
  },
  ivrBind(data: { extension: string; workNo: string }): Promise<IvrBindResultVO> {
    return apiClient.post('/extensions/ivr-bind', data);
  },
};
