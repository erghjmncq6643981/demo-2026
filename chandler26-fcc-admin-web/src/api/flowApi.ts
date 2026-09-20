import apiClient from "./apiClient";
import type { PageResult } from "../shared/api/page";

export interface FlowVersionVO {
  id: string;
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
  currentVersion?: string;
  system: boolean;
}

export interface FlowSaveDraftReq {
  definitionJson: string;
}

export interface FlowPublishReq {
  version: string;
  remark?: string;
}

export interface FlowActionVO {
  code: string;
  label: string;
  executorType: string;
  executorTypeLabel: string;
  operation: string;
}

export interface SystemFlowModelVO {
  template: string;
  definition: {
    nodes?: Array<{
      key: string;
      label: string;
      action: string;
      actionLabel?: string;
      executorType?: string;
      executorTypeLabel?: string;
      operation?: string;
    }>;
  };
}

export interface FlowPublishResp {
  version: string;
  publishStatus: string;
  runtimeActivationStatus: string;
}

export interface FlowExecutionResp {
  instance: {
    id?: string;
    callId?: string;
    versionId?: string;
    status?: string;
    currentStep?: string;
    snapshot?: string;
    startedAt?: string;
    endedAt?: string;
  };
  steps: Array<{
    id: string;
    stepKey: string;
    actionType: string;
    attemptNo: number;
    status: string;
    commandId?: string;
    eventId?: string;
    input?: string;
    output?: string;
    errorCode?: string;
    startedAt: string;
    endedAt?: string;
    durationMs?: number;
  }>;
  nextCursor: string;
}

export const flowApi = {
  create(flowKey: string, flowName: string): Promise<FlowDefinitionVO> {
    return apiClient.post("/flow-studio/flows", { flowKey, flowName });
  },
  list(params: {
    pageNum: number;
    pageSize: number;
  }): Promise<PageResult<FlowDefinitionVO>> {
    return apiClient.get("/flow-studio/flows", { params });
  },
  getVersions(
    flowKey: string,
    params: { pageNum: number; pageSize: number },
  ): Promise<PageResult<FlowVersionVO>> {
    return apiClient.get(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/versions`,
      { params },
    );
  },
  getVersion(flowKey: string, versionNo: number): Promise<FlowVersionVO> {
    return apiClient.get(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/versions/${versionNo}`,
    );
  },
  saveDraft(flowKey: string, data: FlowSaveDraftReq): Promise<FlowVersionVO> {
    return apiClient.put(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/draft`,
      data,
    );
  },
  publish(flowKey: string, data: FlowPublishReq): Promise<FlowPublishResp> {
    return apiClient.post(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/publish`,
      data,
    );
  },
  actions(): Promise<FlowActionVO[]> {
    return apiClient.get("/flow-studio/actions");
  },
  model(template: string): Promise<SystemFlowModelVO> {
    return apiClient.get(`/flow-studio/models/${encodeURIComponent(template)}`);
  },
  execution(callId: string, after: string): Promise<FlowExecutionResp> {
    return apiClient.get(`/flow-studio/calls/${encodeURIComponent(callId)}`, {
      params: { after },
    });
  },
};
