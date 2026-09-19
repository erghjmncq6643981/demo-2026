import apiClient from './apiClient';

export interface FlowVersionVO {
  version: string;
  versionNo: number;
  publishStatus: string;
  definitionJson?: string;
  publishedAt?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface FlowDefinitionVO {
  id: string;
  flowKey: string;
  flowName: string;
  modelType: string;
  status: string;
  currentVersion: string;
  versions: FlowVersionVO[];
}

export interface FlowSaveDraftReq {
  version?: string;
  routeMode?: string;
  definitionJson: string;
}

export interface FlowPublishReq {
  version: string;
  remark?: string;
}

export interface FlowSimulateReq {
  flowKey: string;
  caller?: string;
  did?: string;
  dtmf?: string;
  routeMode?: string;
}

export interface FlowSimulateRespVO {
  success: boolean;
  simulationId: string | null;
  decisionResult: string;
  targetAgentWorkNo: string | null;
  targetAgentName: string | null;
  traces: any[];
}

export const flowApi = {
  list(): Promise<FlowDefinitionVO[]> {
    return apiClient.get('/flows');
  },
  getVersions(flowKey: string): Promise<FlowVersionVO[]> {
    return apiClient.get(`/flows/${flowKey}/versions`);
  },
  saveDraft(flowKey: string, data: FlowSaveDraftReq): Promise<string> {
    return apiClient.post(`/flows/${flowKey}/draft`, data);
  },
  publish(flowKey: string, data: FlowPublishReq): Promise<string> {
    return apiClient.post(`/flows/${flowKey}/publish`, data);
  },
  simulate(data: FlowSimulateReq): Promise<FlowSimulateRespVO> {
    return apiClient.post('/flows/simulate', data);
  },
};
