import apiClient from './apiClient';

export interface ExtensionVO {
  id: number;
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

export interface PageResult<T> {
  pageNum: number;
  pageSize: number;
  total: number;
  list: T[];
}

export interface IvrBindReq {
  extension: string;
  workNo: string;
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
  get(extension: string): Promise<ExtensionVO> {
    return apiClient.get(`/extensions/${extension}`);
  },
  create(data: ExtensionCreateReq): Promise<number> {
    return apiClient.post('/extensions', data);
  },
  delete(id: number): Promise<void> {
    return apiClient.delete(`/extensions/${id}`);
  },
  ivrBind(data: IvrBindReq): Promise<IvrBindResultVO> {
    return apiClient.post('/extensions/ivr-bind', data);
  },
};
